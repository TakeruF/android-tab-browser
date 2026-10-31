#!/usr/bin/env python3
"""Inspect synthetic text in isolated Nagi WebViews; never save page text or credentials."""
import argparse
import contextlib
import fcntl
import hashlib
import json
import pathlib
import re
import subprocess
import tempfile
import threading
import time
import urllib.parse
import urllib.request
import uuid

try:
    import websocket
except ImportError:
    websocket = None

DEFAULT_CASES = ['fixture-desktop', 'fixture-mobile', 'chatgpt-desktop',
                 'chatgpt-mobile', 'chatgpt-no-blocking', 'docs-desktop']
CASES = DEFAULT_CASES + ['controls', 'controls-no-blocking', 'copy-controls', 'api-controls']
DEFAULT_PACKAGE = 'com.takeruf.nagi'
NATIVE_SANS_KEYS = {'sans-serif': 'sans', 'Noto Sans CJK JP': 'jp-family', 'NotoSansCJKJP-Regular': 'jp-postscript'}
LIBRARY = pathlib.Path(__file__).with_name('font_probe.js')


@contextlib.contextmanager
def device_lock(serial, directory=None):
    name = 'nagi-font-probe-' + hashlib.sha256(serial.encode()).hexdigest()[:16] + '.lock'
    with (pathlib.Path(directory or tempfile.gettempdir()) / name).open('a') as handle:
        try:
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise DiagnosticError('device_diagnostic_already_running') from None
        try:
            yield
        finally:
            fcntl.flock(handle, fcntl.LOCK_UN)


class DiagnosticError(RuntimeError):
    """Only a fixed category is exposed; CDP exception text can contain private data."""
    def __init__(self, reason):
        self.reason = reason
        super().__init__(reason)


def require(condition, reason):
    if not condition:
        raise DiagnosticError(reason)


class CDP:
    def __init__(self, url=None, *, transport=None, timeout=15, clock=time.monotonic):
        self.timeout, self.clock = timeout, clock
        if transport is None:
            require(websocket is not None, 'websocket_dependency_missing')
            try:
                transport = websocket.create_connection(url, timeout=timeout, suppress_origin=True)
            except Exception:
                raise DiagnosticError('transport_error') from None
        self.ws = transport
        self.n = 0
        self.font_responses, self.font_requests, self.font_failure_categories = [], {}, {}

    def close(self):
        try:
            self.ws.close()
        except Exception:
            pass

    def _event(self, data):
        p = data.get('params', {})
        if not isinstance(p, dict):
            return
        if data.get('method') == 'Network.requestWillBeSent' and p.get('type') == 'Font':
            try:
                uri = urllib.parse.urlparse(p.get('request', {}).get('url', ''))
                category = ({'/missing.ttf': 'missing', '/invalid.ttf': 'invalid', '/latin.ttf': 'latin',
                             '/latin-italic.ttf': 'italic', '/assets/ads/font-probe.ttf': 'rule'}
                            .get(uri.path, 'other') if uri.hostname == '127.0.0.1' else 'public')
            except (ValueError, TypeError):
                category = 'other'
            self.font_requests[p.get('requestId')] = category
        if data.get('method') == 'Network.responseReceived' and p.get('type') == 'Font':
            response = p.get('response', {})
            mime = response.get('mimeType')
            if mime not in {'font/ttf', 'font/woff', 'font/woff2', 'font/otf', 'application/octet-stream',
                            'application/font-sfnt', 'text/plain'}:
                mime = 'other'
            self.font_responses.append({'status': response.get('status'), 'mimeType': mime,
                                        'category': self.font_requests.get(p.get('requestId'), 'other')})
        if data.get('method') == 'Network.loadingFailed' and p.get('requestId') in self.font_requests:
            category = self.font_requests[p['requestId']]
            self.font_failure_categories[category] = self.font_failure_categories.get(category, 0) + 1

    def call(self, method, params=None):
        self.n += 1
        deadline = self.clock() + self.timeout
        try:
            self.ws.send(json.dumps({'id': self.n, 'method': method, 'params': params or {}}))
            while True:
                remaining = deadline - self.clock()
                require(remaining > 0, 'cdp_timeout')
                self.ws.settimeout(remaining)
                data = json.loads(self.ws.recv())
                require(isinstance(data, dict), 'invalid_cdp_response')
                self._event(data)
                if data.get('id') == self.n:
                    require('error' not in data, 'cdp_protocol_error')
                    require(isinstance(data.get('result'), dict), 'invalid_cdp_response')
                    return data['result']
        except DiagnosticError:
            raise
        except Exception:
            if self.clock() >= deadline:
                raise DiagnosticError('cdp_timeout') from None
            raise DiagnosticError('cdp_transport_or_response_error') from None

    def evaluate(self, expression, *, allow_none=False):
        response = self.call('Runtime.evaluate', {'expression': expression, 'returnByValue': True,
                                                    'awaitPromise': True})
        require('exceptionDetails' not in response, 'javascript_exception')
        remote = response.get('result')
        require(isinstance(remote, dict), 'invalid_javascript_result')
        require(remote.get('type') != 'undefined', 'undefined_javascript_result')
        require('value' in remote, 'nonserializable_javascript_result')
        value = remote['value']
        require(value is not None or allow_none, 'null_javascript_result')
        return value

    def network_snapshot(self):
        return {'responses': self.font_responses, 'requestsObserved': len(self.font_requests),
                'failures': sum(self.font_failure_categories.values()),
                'failureCategories': self.font_failure_categories}


def js_call(method, *args):
    return 'window.__nagiFontProbeApi.' + method + '(' + ','.join(json.dumps(a) for a in args) + ')'


def context_guard(client, nonce, capture_id):
    require(client.evaluate(js_call('context', nonce, capture_id)) is True, 'capture_changed')


def query_owned_node(client, root, selector):
    node = client.call('DOM.querySelector', {'nodeId': root, 'selector': selector}).get('nodeId')
    require(isinstance(node, int) and node > 0, 'missing_synthetic_node')
    return node


def validate_nodes(nodes, expected):
    require(isinstance(nodes, list) and isinstance(expected, list) and expected, 'incomplete_nodes')
    expected_keys = {(n['root'], n['selector']) for n in expected}
    keys = [(n.get('root'), n.get('selector')) for n in nodes]
    require(len(keys) == len(set(keys)) and set(keys) == expected_keys, 'incomplete_nodes')
    for node in nodes:
        fonts = node.get('fonts')
        require(isinstance(fonts, list) and any(isinstance(f, dict) and f.get('glyphCount', 0) > 0
                                               for f in fonts), 'no_rendered_font')
        require(isinstance(node.get('style'), dict), 'missing_font_style')
        source_style = node.get('sourceStyle')
        if source_style is not None:
            require(node['style'] == source_style, 'copied_font_style_mismatch')


def validate_instrumentation(text, returncode):
    require(returncode == 0 and 'OK (1 test)' in text and
            'INSTRUMENTATION_STATUS_CODE: -4' not in text, 'instrumentation_failed_or_skipped')


def write_result(path, value):
    temporary = path.with_name(path.name + '.tmp')
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding='utf-8')
    temporary.replace(path)


def font_signature(node):
    return {(f['familyName'], f.get('postScriptName'), f['isCustomFont']) for f in node['fonts']}


def validate_controls(result, state):
    nodes = {n['selector'].removeprefix('#control-'): n for n in result['nodes']}
    actual = lambda name: font_signature(nodes[name])
    custom = lambda name: any(f['isCustomFont'] and f['glyphCount'] > 0 for f in nodes[name]['fonts'])
    faces = {f['family'].strip('"'): f['status'] for f in result['metadata']['faces']}
    statuses = {category: {r['status'] for r in result['fontNetwork']['responses'] if r['category'] == category}
                for category in ['missing', 'invalid', 'latin', 'rule']}
    require(statuses == {'missing': {404}, 'invalid': {200}, 'latin': {200}, 'rule': {200}}, 'control_http_mismatch')
    require(result['metadata']['fontStatus'] == 'loaded' and len(nodes) == 8, 'incomplete_controls')
    require(actual('baseline-sans') != actual('baseline-serif'), 'serif_control_not_detected')
    require(actual('missing-sans') == actual('baseline-sans'), 'sans_fallback_control_failed')
    require(actual('missing-serif') == actual('baseline-serif'), 'serif_fallback_control_failed')
    require(actual('invalid-sans') == actual('baseline-sans'), 'invalid_font_control_failed')
    require(faces['ProbeMissing'] == faces['ProbeInvalid'] == 'error', 'font_errors_not_detected')
    require(faces['ProbeLatinOnly'] == 'loaded' and custom('latin-only-latin'), 'custom_font_not_detected')
    require(not custom('latin-only-japanese') and actual('latin-only-japanese') == actual('baseline-sans'), 'unicode_control_failed')
    require(result['checks']['latinWithJapanese'] is True, 'font_check_control_failed')
    blocked = result['case'] == 'controls'
    require(faces['ProbeRule'] == ('error' if blocked else 'loaded') and custom('rule-latin') != blocked, 'rule_font_control_failed')
    require((state['blockedRequests'] > 0) == blocked, 'engine_block_count_mismatch')
    require((state['serverRequests'].get('rule', 0) > 0) != blocked, 'blocked_font_reached_server')


def validate_copy_controls(result):
    nodes = {n['selector']: n for n in result['nodes']}
    geometry = {n['selector']: n for n in result['localeControls']}
    for name in ['normal', 'italic', 'japanese', 'chinese']:
        reference = nodes['#copy-reference-' + name]
        for prefix in ['#font-probe-', '#font-probe-document-']:
            probe = nodes[prefix + name]
            require(reference['style'] == probe['style'], 'copied_font_style_mismatch')
            require(font_signature(reference) == font_signature(probe), 'copied_font_usage_mismatch')
            for axis in ['width', 'height']:
                require(abs(geometry[reference['selector']][axis] - geometry[probe['selector']][axis]) < .1, 'copied_text_metrics_mismatch')
    require(font_signature(nodes['#copy-reference-normal']) != font_signature(nodes['#copy-reference-italic']), 'italic_control_not_detected')
    reference = nodes['#copy-reference-japanese']
    fixed = nodes['#font-probe-reset-fixed-japanese']
    require(reference['style'] == fixed['style'] and font_signature(reference) == font_signature(fixed), 'locale_copy_control_failed')
    require(nodes['#font-probe-reset-japanese']['style']['properties']['-webkit-locale'] !=
            reference['style']['properties']['-webkit-locale'], 'locale_loss_control_not_detected')
    require(font_signature(nodes['#font-probe-legacy-italic']) != font_signature(nodes['#copy-reference-italic']), 'legacy_style_loss_not_detected')
    variable, lost = nodes['#copy-reference-normal'], nodes['#font-probe-variation-lost']
    require(font_signature(variable) == font_signature(lost), 'variation_control_font_changed')
    require(abs(geometry[variable['selector']]['width'] - geometry[lost['selector']]['width']) > 1, 'variation_loss_not_detected')


def apply_generic_sans_control(client, label, family):
    require(label.startswith('fixture'), 'font_default_scope_rejected')
    require(client.evaluate('location.hostname') == '127.0.0.1', 'font_default_scope_rejected')
    client.call('Page.setFontFamilies', {'fontFamilies': {'sansSerif': family}})


def capture_fonts(client, label, nonce, *, delay=time.sleep, matrix_stack=None, generic_sans=None):
    client.call('DOM.enable'); client.call('CSS.enable'); client.call('Network.enable')
    client.call('Page.reload', {'ignoreCache': True})
    delay(2 if label.startswith(('fixture', 'controls', 'copy-', 'api-')) else 16)
    require(client.evaluate('window.__nagiFontProbeNonce||null', allow_none=True) == nonce, 'target_marker_changed')
    if generic_sans is not None:
        apply_generic_sans_control(client, label, generic_sans)
    client.evaluate(LIBRARY.read_text(encoding='utf-8'))
    client.evaluate('window.__nagiFontProbeApi.waitForFonts(document.fonts,8000)')
    capture_id = str(uuid.uuid4())
    layout = client.evaluate(js_call('begin', nonce, capture_id, label))
    if matrix_stack is not None:
        layout = client.evaluate(js_call('matrix', nonce, capture_id, matrix_stack))
    require(isinstance(layout, dict) and isinstance(layout.get('expected'), list), 'invalid_capture_layout')
    client.evaluate('window.__nagiFontProbeApi.waitForFonts(document.fonts,8000)')
    context_guard(client, nonce, capture_id)
    document = client.call('DOM.getDocument', {'depth': 0})['root']['nodeId']
    roots = {'document': document}
    if layout.get('hostId'):
        host = client.call('DOM.querySelector', {'nodeId': document, 'selector': '#' + layout['hostId']})['nodeId']
        require(host > 0, 'missing_probe_host')
        shadow = client.call('DOM.describeNode', {'nodeId': host, 'depth': 1, 'pierce': True})['node'].get('shadowRoots', [])
        require(len(shadow) == 1, 'missing_probe_shadow_root')
        shadow_id = shadow[0].get('nodeId', 0)
        if not shadow_id:
            pushed = client.call('DOM.pushNodesByBackendIdsToFrontend', {'backendNodeIds': [shadow[0]['backendNodeId']]})
            shadow_id = pushed['nodeIds'][0]
        require(shadow_id > 0, 'missing_probe_shadow_root')
        roots['shadow'] = shadow_id
    result = {'case': label, 'metadata': layout['metadata'], 'nodes': []}
    if generic_sans is not None:
        result['metadata']['cdpGenericSansControl'] = generic_sans
    for spec in layout['expected']:
        context_guard(client, nonce, capture_id)
        node = query_owned_node(client, roots[spec['root']], spec['selector'])
        fonts = client.call('CSS.getPlatformFontsForNode', {'nodeId': node})['fonts']
        style = client.evaluate(js_call('read', nonce, capture_id, spec['selector'], spec['root']))
        result['nodes'].append({'selector': spec['selector'], 'root': spec['root'], 'fonts': fonts,
                                'style': style, **({'sourceStyle': spec['sourceStyle']} if 'sourceStyle' in spec else {}),
                                **({'controlRole': spec['controlRole']} if 'controlRole' in spec else {}),
                                **({'matrixSpec': spec['matrixSpec']} if 'matrixSpec' in spec else {})})
    context_guard(client, nonce, capture_id)
    result['metadata']['faces'] = client.evaluate('[...document.fonts].map(f=>({family:f.family,status:f.status,weight:f.weight}))')
    result['metadata']['fontStatus'] = client.evaluate('document.fonts.status')
    require(result['metadata']['fontStatus'] == 'loaded', 'font_capture_still_loading')
    if label.startswith('controls'):
        result['checks'] = client.evaluate("({latinWithJapanese:document.fonts.check('32px ProbeLatinOnly','日本語')})")
    if label == 'copy-controls':
        result['localeControls'] = client.evaluate(js_call('localeControls', nonce, capture_id))
    context_guard(client, nonce, capture_id)
    validate_nodes(result['nodes'], layout['expected'])
    result['fontNetwork'] = client.network_snapshot()
    return result


class Device:
    def __init__(self, serial, port, package=DEFAULT_PACKAGE):
        require(valid_package(package), 'invalid_package')
        require(isinstance(serial, str) and bool(serial) and '\n' not in serial and '\r' not in serial, 'invalid_device_selector')
        self.serial, self.port = serial, str(port)
        self.package = package
        self.remote = '/sdcard/Android/data/' + package + '/files/font-compatibility'
        self.runner = package + '.test/androidx.test.runner.AndroidJUnitRunner'
        self.matrix_stack = None
        self.generic_sans = None
        self.plain_webview = False
        self.native_sans = None

    def adb(self, *args):
        return subprocess.run(['adb', '-s', self.serial, *args], capture_output=True, text=True)

    def targets(self):
        pids = self.adb('shell', 'pidof', self.package).stdout.split()
        require(len(pids) == 1, 'diagnostic_process_missing')
        require(self.adb('forward', 'tcp:' + self.port, 'localabstract:webview_devtools_remote_' + pids[0]).returncode == 0, 'forward_failed')
        with urllib.request.urlopen('http://127.0.0.1:' + self.port + '/json/list', timeout=3) as response:
            value = json.load(response)
        require(isinstance(value, list), 'invalid_target_inventory')
        return value


def candidate_url(label, url):
    try:
        uri = urllib.parse.urlparse(url)
        if label.startswith('fixture'):
            return uri.hostname == '127.0.0.1' and uri.path == '/font-fixture'
        if label.startswith(('controls', 'copy-', 'api-')):
            return uri.hostname == '127.0.0.1' and uri.path in {'/font-controls', '/font-copy-controls', '/font-api-controls'}
        if label.startswith('chatgpt'):
            return uri.scheme == 'https' and uri.hostname == 'chatgpt.com'
        return uri.scheme == 'https' and uri.hostname in {'accounts.google.com', 'docs.google.com'}
    except ValueError:
        return False


def locate_target(device, proc, label, *, deadline_seconds=60):
    deadline = time.monotonic() + deadline_seconds
    while time.monotonic() < deadline:
        require(proc.poll() is None, 'instrumentation_stopped_before_capture')
        try:
            ready = json.loads(device.adb('shell', 'cat', device.remote + '/ready.json').stdout)
            if ready.get('case') != label:
                time.sleep(.2); continue
            nonce = ready.get('nonce')
            require(isinstance(nonce, str) and nonce, 'isolation_marker_missing')
            for target in device.targets():
                if not candidate_url(label, target.get('url', '')):
                    continue
                candidate = CDP(target['webSocketDebuggerUrl'])
                try:
                    if candidate.evaluate('window.__nagiFontProbeNonce||null', allow_none=True) == nonce:
                        return target['webSocketDebuggerUrl'], nonce
                finally:
                    candidate.close()
        except DiagnosticError as error:
            if error.reason == 'isolation_marker_missing':
                raise
        except (OSError, ValueError, KeyError):
            pass
        time.sleep(.2)
    raise DiagnosticError('isolated_target_timeout')


def api_controls(client):
    checks = [('javascript_exception', "(()=>{throw new Error('synthetic private detail')})()"),
              ('javascript_exception', "Promise.reject(new Error('synthetic private detail'))"),
              ('undefined_javascript_result', 'undefined'),
              ('javascript_exception', "window.__nagiFontProbeApi.waitForFonts({ready:new Promise(()=>{}),status:'loading'},20)")]
    outcomes = []
    for expected, expression in checks:
        try:
            client.evaluate(expression)
        except DiagnosticError as error:
            require(error.reason == expected, 'api_negative_control_mismatch')
            outcomes.append(expected)
        else:
            raise DiagnosticError('api_failure_was_accepted')
    old_timeout = client.timeout
    client.timeout = .1
    try:
        try:
            client.evaluate('new Promise(()=>{})')
        except DiagnosticError as error:
            require(error.reason == 'cdp_timeout', 'api_timeout_control_mismatch')
            outcomes.append(error.reason)
        else:
            raise DiagnosticError('api_timeout_was_accepted')
    finally:
        client.timeout = old_timeout
    document = client.call('DOM.getDocument', {'depth': 0})['root']['nodeId']
    try:
        query_owned_node(client, document, '#intentionally-missing-synthetic-probe')
    except DiagnosticError as error:
        require(error.reason == 'missing_synthetic_node', 'api_missing_node_control_mismatch')
        outcomes.append(error.reason)
    else:
        raise DiagnosticError('api_missing_node_was_accepted')
    client.evaluate("window.__nagiFontProbeCaptureId='intentionally-changed';true")
    try:
        context_guard(client, 'intentionally-wrong-nonce', 'intentionally-wrong-generation')
    except DiagnosticError as error:
        require(error.reason == 'capture_changed', 'api_generation_control_mismatch')
        outcomes.append(error.reason)
    else:
        raise DiagnosticError('api_generation_change_was_accepted')
    return outcomes


def run_case(device, output, label):
    path = output / (label + '-fonts.json')
    write_result(path, {'case': label, 'status': 'in_progress'})
    device.adb('shell', 'rm', '-f', device.remote + '/ready.json', device.remote + '/done', device.remote + '/' + label + '-state.json')
    log_path = output / (label + '-instrumentation.txt')
    client, result, failure = None, None, None
    with log_path.open('w') as log:
        proc = subprocess.Popen(instrumentation_command(device, label), stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
        reader = threading.Thread(target=copy_instrumentation_output, args=(proc.stdout, log, device.serial), daemon=True)
        reader.start()
        try:
            target_url, nonce = locate_target(device, proc, label)
            # Reconnect, then recheck the document marker before reading any font styles.
            client = CDP(target_url)
            require(client.evaluate('window.__nagiFontProbeNonce||null', allow_none=True) == nonce, 'target_marker_changed')
            result = capture_fonts(client, label, nonce, matrix_stack=device.matrix_stack, generic_sans=device.generic_sans)
            require(isinstance(result, dict) and bool(result.get('nodes')), 'incomplete_nodes')
            if label == 'api-controls':
                result['apiNegativeControls'] = api_controls(client)
        except DiagnosticError as error:
            failure = error
        except Exception:
            failure = DiagnosticError('unexpected_diagnostic_error')
        finally:
            if client:
                client.close()
            device.adb('shell', 'touch', device.remote + '/done')
            try:
                proc.wait(timeout=25)
            except subprocess.TimeoutExpired:
                proc.terminate(); proc.wait(timeout=5)
                failure = failure or DiagnosticError('instrumentation_exit_timeout')
            reader.join(timeout=2)
            if reader.is_alive():
                failure = failure or DiagnosticError('instrumentation_output_incomplete')
            device.adb('forward', '--remove', 'tcp:' + device.port)
    try:
        if failure:
            raise failure
        validate_instrumentation(log_path.read_text(), proc.returncode)
        for suffix in ['settings', 'state']:
            require(device.adb('pull', device.remote + '/' + label + '-' + suffix + '.json', str(output / (label + '-' + suffix + '.json'))).returncode == 0,
                    'diagnostic_state_missing')
        state = json.loads((output / (label + '-state.json')).read_text())
        settings = json.loads((output / (label + '-settings.json')).read_text())
        if device.native_sans is not None:
            require(settings.get('sansSerif') == device.native_sans and settings.get('nativeSansControl') == device.native_sans,
                    'native_sans_readback_mismatch')
            result['metadata']['nativeSansControl'] = device.native_sans
        require(state.get('plainWebView', False) == device.plain_webview, 'webview_control_mismatch')
        result['metadata']['plainWebView'] = device.plain_webview
        if label.startswith('controls'):
            validate_controls(result, state)
            result['controlAssertions'] = 'passed'
            result['diagnosticState'] = state
        if label == 'copy-controls':
            validate_copy_controls(result)
            result['copyAssertions'] = 'passed'
        result['status'] = 'complete'
        write_result(path, result)
        print(label + ': complete', flush=True)
        return True
    except DiagnosticError as error:
        failure_result = {'case': label, 'status': 'failed', 'reason': error.reason}
        if result is not None:
            failure_result['captured'] = result
        write_result(path, failure_result)
        print(label + ': failed (' + error.reason + ')', flush=True)
        return False
    except Exception:
        write_result(path, {'case': label, 'status': 'failed', 'reason': 'invalid_diagnostic_state'})
        print(label + ': failed (invalid_diagnostic_state)', flush=True)
        return False


def compare_controls(output):
    pair = [json.loads((output / (name + '-fonts.json')).read_text()) for name in ['controls', 'controls-no-blocking']]
    require(all(value.get('status') == 'complete' for value in pair), 'control_pair_incomplete')
    def stable(value):
        return {n['selector']: {'style': n['style'], 'fonts': n['fonts']} for n in value['nodes'] if n['selector'] != '#control-rule-latin'}
    require(stable(pair[0]) == stable(pair[1]), 'unrelated_controls_changed')
    print('controls pair: seven unrelated nodes unchanged', flush=True)


def valid_package(value):
    return isinstance(value, str) and re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+', value) is not None


def instrumentation_command(device, label):
    require(not device.plain_webview or label.startswith('fixture'), 'plain_webview_scope_rejected')
    require(device.native_sans is None or device.plain_webview, 'native_sans_scope_rejected')
    return ['adb', '-s', device.serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
            'com.takeruf.nagi.FontCompatibilityTest', '-e', 'fontProbe', 'true', '-e', 'fontCase', label,
            *(['-e', 'fontPlain', 'true'] if device.plain_webview else []),
            *(['-e', 'fontNativeSans', NATIVE_SANS_KEYS[device.native_sans]] if device.native_sans else []),
            device.runner]


def read_matrix_stack(path):
    try:
        value = json.loads(path.read_text())
        require(isinstance(value, dict), 'invalid_matrix_stack_source')
        require(isinstance(value.get('nodes'), list) and all(isinstance(node, dict) for node in value['nodes']), 'invalid_matrix_stack_source')
    except (OSError, ValueError, TypeError):
        raise DiagnosticError('invalid_matrix_stack_source')
    require(value.get('status') == 'complete' and value.get('case', '').startswith('chatgpt'), 'invalid_matrix_stack_source')
    rows = [node for node in value.get('nodes', []) if node.get('selector') == '#font-probe-body']
    require(len(rows) == 1, 'invalid_matrix_stack_source')
    stack = rows[0].get('style', {}).get('properties', {}).get('font-family')
    require(isinstance(stack, str) and 0 < len(stack) <= 2048, 'invalid_matrix_stack_source')
    return stack


def copy_instrumentation_output(lines, output, serial):
    """Redact before writing: adb itself may print the selected device ID on errors."""
    for line in lines:
        output.write(line.replace(serial, '<device>'))
        output.flush()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--package', default=DEFAULT_PACKAGE, help='Diagnostic application ID (its test ID is PACKAGE.test)')
    parser.add_argument('--port', type=int, default=9236)
    parser.add_argument('--output', type=pathlib.Path, default=pathlib.Path('artifacts/font-compatibility'))
    parser.add_argument('--list-cases', action='store_true')
    parser.add_argument('--font-matrix', action='store_true', help='Add a synthetic language/family matrix to localhost fixture cases only')
    parser.add_argument('--matrix-stack', type=pathlib.Path, help='Previously completed ChatGPT font JSON providing its CSS stack')
    parser.add_argument('--generic-sans-control', choices=['sans-serif', 'Noto Sans CJK JP', 'NotoSansCJKJP-Regular'],
                        help='Experimental CDP generic sans override, only on localhost matrix fixtures')
    parser.add_argument('--plain-webview', action='store_true', help='Use isolated plain Android WebView with default settings, fixture cases only')
    parser.add_argument('--native-sans-control', choices=['sans-serif', 'Noto Sans CJK JP', 'NotoSansCJKJP-Regular'],
                        help='Native WebSettings sansSerif override, only for plain localhost WebView')
    parser.add_argument('cases', nargs='*', metavar='CASE')
    options = parser.parse_args(argv)
    if not 1 <= options.port <= 65535:
        parser.error('--port must be between 1 and 65535')
    if not valid_package(options.package):
        parser.error('Invalid Android package name')
    if any(case not in CASES for case in options.cases):
        parser.error('Unknown case; choose from ' + ', '.join(CASES))
    labels = options.cases or DEFAULT_CASES
    if options.list_cases:
        print('\n'.join(labels)); return 0
    if options.font_matrix and (not options.matrix_stack or any(not label.startswith('fixture') for label in labels)):
        parser.error('--font-matrix requires --matrix-stack and only fixture cases')
    if options.matrix_stack and not options.font_matrix:
        parser.error('--matrix-stack requires --font-matrix')
    if options.generic_sans_control and not options.font_matrix:
        parser.error('--generic-sans-control requires --font-matrix')
    if options.plain_webview and any(not label.startswith('fixture') for label in labels):
        parser.error('--plain-webview requires only fixture cases')
    if options.native_sans_control and (not options.plain_webview or options.generic_sans_control):
        parser.error('--native-sans-control requires --plain-webview and cannot combine with CDP override')
    try:
        with device_lock(options.serial):
            options.output.mkdir(parents=True, exist_ok=True)
            device = Device(options.serial, options.port, options.package)
            device.generic_sans = options.generic_sans_control
            device.plain_webview = options.plain_webview
            device.native_sans = options.native_sans_control
            if options.font_matrix:
                device.matrix_stack = read_matrix_stack(options.matrix_stack)
            successful = all([run_case(device, options.output, label) for label in labels])
            if {'controls', 'controls-no-blocking'}.issubset(labels) and successful:
                compare_controls(options.output)
            return 0 if successful else 1
    except DiagnosticError as error:
        print('diagnostic: failed (' + error.reason + ')'); return 1


if __name__ == '__main__':
    raise SystemExit(main())
