import contextlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / 'inspect_font_compatibility.py'
spec = importlib.util.spec_from_file_location('font_probe', MODULE)
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class Socket:
    def __init__(self, messages):
        self.messages = iter(messages)
        self.sent = []
    def send(self, value): self.sent.append(json.loads(value))
    def recv(self): return json.dumps(next(self.messages))
    def settimeout(self, value): pass
    def close(self): pass


def evaluated(remote, **extra):
    return {'id': 1, 'result': {'result': remote, **extra}}


class ProtocolFailures(unittest.TestCase):
    def error(self, message, expected, allow_none=False):
        client = probe.CDP(transport=Socket([message]))
        with self.assertRaises(probe.DiagnosticError) as caught:
            client.evaluate('synthetic expression', allow_none=allow_none)
        self.assertEqual(expected, caught.exception.reason)
        self.assertNotIn('PRIVATE_SENTINEL', str(caught.exception))

    def test_javascript_exception_is_not_a_successful_undefined_value(self):
        self.error(evaluated({'type': 'undefined'}, exceptionDetails={'text': 'PRIVATE_SENTINEL'}), 'javascript_exception')

    def test_rejected_promise_exception_is_sanitized(self):
        self.error(evaluated({'type': 'object', 'subtype': 'error', 'description': 'PRIVATE_SENTINEL'},
                             exceptionDetails={'exception': {'description': 'PRIVATE_SENTINEL'}}), 'javascript_exception')

    def test_undefined_is_rejected(self):
        self.error(evaluated({'type': 'undefined'}), 'undefined_javascript_result')

    def test_unserializable_objects_are_rejected(self):
        self.error(evaluated({'type': 'object', 'objectId': 'PRIVATE_SENTINEL'}), 'nonserializable_javascript_result')

    def test_null_requires_an_explicit_nullable_contract(self):
        self.error(evaluated({'type': 'object', 'subtype': 'null', 'value': None}), 'null_javascript_result')
        client = probe.CDP(transport=Socket([evaluated({'type': 'object', 'subtype': 'null', 'value': None})]))
        self.assertIsNone(client.evaluate('null', allow_none=True))

    def test_protocol_errors_do_not_print_remote_error_text(self):
        self.error({'id': 1, 'error': {'code': -32000, 'message': 'PRIVATE_SENTINEL'}}, 'cdp_protocol_error')

    def test_total_deadline_cannot_be_extended_by_continuous_events(self):
        now = [0.0]
        def clock():
            now[0] += .2
            return now[0]
        events = [{'method': 'Network.loadingFinished', 'params': {}}] * 100
        client = probe.CDP(transport=Socket(events), timeout=1, clock=clock)
        with self.assertRaises(probe.DiagnosticError) as caught:
            client.call('Runtime.evaluate')
        self.assertEqual('cdp_timeout', caught.exception.reason)
        self.assertLess(now[0], 2)

    def test_valid_false_is_a_value_and_not_a_transport_failure(self):
        client = probe.CDP(transport=Socket([evaluated({'type': 'boolean', 'value': False})]))
        self.assertIs(client.evaluate('false'), False)


class CompletenessAndPublication(unittest.TestCase):
    def node(self, selector='#owned'):
        return {'root': 'document', 'selector': selector,
                'fonts': [{'familyName': 'Synthetic Font', 'glyphCount': 3, 'isCustomFont': False}],
                'style': {'properties': {'font-style': 'italic'}, 'lang': 'ja'}}

    def test_missing_node_cannot_be_accepted_as_partial_success(self):
        with self.assertRaises(probe.DiagnosticError) as caught:
            probe.validate_nodes([], [{'root': 'document', 'selector': '#owned'}])
        self.assertEqual('incomplete_nodes', caught.exception.reason)

    def test_missing_dom_target_aborts_capture_instead_of_continuing(self):
        class Client:
            def evaluate(self, expression, **kwargs):
                if expression == 'window.__nagiFontProbeNonce||null': return 'marker'
                if expression.startswith('window.__nagiFontProbeApi.begin'):
                    return {'expected': [{'root': 'document', 'selector': '#owned'}], 'metadata': {}}
                return True
            def call(self, method, params=None):
                if method == 'DOM.getDocument': return {'root': {'nodeId': 1}}
                if method == 'DOM.querySelector': return {'nodeId': 0}
                if method == 'CSS.getPlatformFontsForNode': raise AssertionError('Must not inspect missing node')
                return {}
        with self.assertRaises(probe.DiagnosticError) as caught:
            probe.capture_fonts(Client(), 'fixture-desktop', 'marker', delay=lambda _: None)
        self.assertEqual('missing_synthetic_node', caught.exception.reason)

    def test_duplicate_nodes_cannot_replace_a_missing_node(self):
        with self.assertRaises(probe.DiagnosticError):
            probe.validate_nodes([self.node(), self.node()], [{'root': 'document', 'selector': '#owned'}])

    def test_zero_rendered_glyphs_are_inconclusive(self):
        node = self.node(); node['fonts'][0]['glyphCount'] = 0
        with self.assertRaises(probe.DiagnosticError) as caught:
            probe.validate_nodes([node], [{'root': 'document', 'selector': '#owned'}])
        self.assertEqual('no_rendered_font', caught.exception.reason)

    def test_changed_font_style_is_rejected_even_if_family_matches(self):
        node = self.node()
        node['sourceStyle'] = {'properties': {'font-style': 'normal'}, 'lang': 'ja'}
        with self.assertRaises(probe.DiagnosticError) as caught:
            probe.validate_nodes([node], [{'root': 'document', 'selector': '#owned'}])
        self.assertEqual('copied_font_style_mismatch', caught.exception.reason)

    def test_document_generation_change_is_rejected(self):
        fake = type('Client', (), {'evaluate': lambda self, expression: False})()
        with self.assertRaises(probe.DiagnosticError) as caught:
            probe.context_guard(fake, 'synthetic marker', 'synthetic generation')
        self.assertEqual('capture_changed', caught.exception.reason)

    def test_skipped_instrumentation_is_not_success_even_with_ok_summary(self):
        with self.assertRaises(probe.DiagnosticError):
            probe.validate_instrumentation('INSTRUMENTATION_STATUS_CODE: -4\nOK (1 test)', 0)

    def test_nonzero_process_status_is_not_success(self):
        with self.assertRaises(probe.DiagnosticError):
            probe.validate_instrumentation('OK (1 test)', 1)

    def test_failure_overwrites_prior_success_instead_of_leaving_stale_artifact(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'case-fonts.json'
            probe.write_result(path, {'status': 'complete'})
            probe.write_result(path, {'status': 'failed', 'reason': 'missing_synthetic_node'})
            self.assertEqual('failed', json.loads(path.read_text())['status'])
            self.assertFalse(path.with_name(path.name + '.tmp').exists())

    def test_runner_records_failed_target_lookup_and_cleans_up(self):
        class Device(probe.Device):
            def __init__(self):
                super().__init__('synthetic', 12345, 'com.takeruf.nagi.compatprobe')
                self.calls = []
            def adb(self, *args): self.calls.append(args)
        class Process:
            returncode = 0
            stdout = io.StringIO('')
            def wait(self, timeout): return 0
        device = Device()
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            probe.write_result(path / 'fixture-desktop-fonts.json', {'status': 'complete'})
            with patch.object(probe.subprocess, 'Popen', return_value=Process()):
                with patch.object(probe, 'locate_target', side_effect=probe.DiagnosticError('isolated_target_timeout')):
                    with contextlib.redirect_stdout(io.StringIO()):
                        self.assertFalse(probe.run_case(device, path, 'fixture-desktop'))
            self.assertEqual({'case': 'fixture-desktop', 'status': 'failed', 'reason': 'isolated_target_timeout'},
                             json.loads((path / 'fixture-desktop-fonts.json').read_text()))
        self.assertIn(('forward', '--remove', 'tcp:12345'), device.calls)
        remote = '/sdcard/Android/data/com.takeruf.nagi.compatprobe/files/font-compatibility'
        self.assertIn(('shell', 'touch', remote + '/done'), device.calls)
        self.assertFalse(any('/data/com.takeruf.nagi/' in str(call) for call in device.calls))

    def test_default_cli_is_import_safe_and_lists_six_cases_without_device_calls(self):
        output = io.StringIO()
        with patch.object(probe.subprocess, 'run', side_effect=AssertionError('must not contact device')):
            with contextlib.redirect_stdout(output):
                self.assertEqual(0, probe.main(['--serial', 'synthetic', '--list-cases']))
        self.assertEqual(probe.DEFAULT_CASES, output.getvalue().splitlines())

    def test_url_matching_rejects_lookalike_and_non_loopback_fixture_targets(self):
        self.assertFalse(probe.candidate_url('chatgpt-desktop', 'https://chatgpt.com.example.test/'))
        self.assertFalse(probe.candidate_url('controls', 'https://example.test/font-controls'))
        self.assertTrue(probe.candidate_url('controls', 'http://127.0.0.1:1234/font-controls'))

    def test_same_device_cannot_be_probed_by_two_helpers_at_once(self):
        with tempfile.TemporaryDirectory() as directory:
            with probe.device_lock('synthetic device', directory):
                with self.assertRaises(probe.DiagnosticError) as caught:
                    with probe.device_lock('synthetic device', directory):
                        self.fail('second probe must not enter')
                self.assertEqual('device_diagnostic_already_running', caught.exception.reason)
                with probe.device_lock('different synthetic device', directory):
                    pass
            with probe.device_lock('synthetic device', directory):
                pass


class PackageIsolation(unittest.TestCase):
    def test_successful_case_uses_custom_package_for_runner_and_all_files(self):
        class Device(probe.Device):
            def __init__(self):
                super().__init__('synthetic-device', 9236, 'com.takeruf.nagi.compatprobe')
                self.calls = []
            def adb(self, *args):
                self.calls.append(args)
                if args[0] == 'pull':
                    Path(args[2]).write_text('{}')
                return type('Answer', (), {'returncode': 0})()
        class Process:
            returncode = 0
            stdout = io.StringIO('INSTRUMENTATION_STATUS: device=synthetic-device\nOK (1 test)\n')
            def wait(self, timeout): return 0
        class Client:
            def evaluate(self, expression, **kwargs): return 'marker'
            def close(self): pass
        device = Device()
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            with patch.object(probe.subprocess, 'Popen', return_value=Process()) as start:
                with patch.object(probe, 'locate_target', return_value=('ws://synthetic', 'marker')):
                    with patch.object(probe, 'CDP', return_value=Client()):
                        with patch.object(probe, 'capture_fonts', return_value={'case': 'fixture-desktop', 'metadata': {}, 'nodes': [
                                {'root': 'document', 'selector': '#synthetic', 'fonts': [{'familyName': 'Synthetic Font', 'glyphCount': 3, 'isCustomFont': False}]}]}):
                            with contextlib.redirect_stdout(io.StringIO()):
                                self.assertTrue(probe.run_case(device, output, 'fixture-desktop'))
            self.assertEqual(device.runner, start.call_args.args[0][-1])
            self.assertEqual('complete', json.loads((output / 'fixture-desktop-fonts.json').read_text())['status'])
            log = (output / 'fixture-desktop-instrumentation.txt').read_text()
            self.assertNotIn('synthetic-device', log)
            self.assertIn('device=<device>', log)
        pulls = [call[1] for call in device.calls if call[0] == 'pull']
        self.assertEqual([device.remote + '/fixture-desktop-settings.json', device.remote + '/fixture-desktop-state.json'], pulls)
        self.assertFalse(any('/data/com.takeruf.nagi/' in str(call) for call in device.calls))

    def test_device_identifier_is_redacted_before_log_write(self):
        output = io.StringIO()
        probe.copy_instrumentation_output(io.StringIO("adb: device 'SYNTHETIC_DEVICE' not found\n日本語font\n"), output, 'SYNTHETIC_DEVICE')
        self.assertEqual("adb: device '<device>' not found\n日本語font\n", output.getvalue())
        self.assertNotIn('SYNTHETIC_DEVICE', output.getvalue())

    def test_default_package_remains_compatible(self):
        device = probe.Device('synthetic', 9236)
        self.assertEqual('com.takeruf.nagi', device.package)
        self.assertEqual('/sdcard/Android/data/com.takeruf.nagi/files/font-compatibility', device.remote)
        self.assertEqual('com.takeruf.nagi.test/androidx.test.runner.AndroidJUnitRunner', device.runner)

    def test_custom_package_controls_paths_and_runner_but_not_code_namespace(self):
        package = 'com.takeruf.nagi.compatprobe'
        device = probe.Device('synthetic', 9236, package)
        self.assertEqual('/sdcard/Android/data/' + package + '/files/font-compatibility', device.remote)
        command = probe.instrumentation_command(device, 'fixture-desktop')
        self.assertEqual(package + '.test/androidx.test.runner.AndroidJUnitRunner', command[-1])
        self.assertIn('com.takeruf.nagi.FontCompatibilityTest', command)
        self.assertNotIn('com.takeruf.nagi.test/androidx.test.runner.AndroidJUnitRunner', command)

    def test_pid_lookup_uses_only_the_selected_package(self):
        package = 'com.takeruf.nagi.compatprobe'
        device = probe.Device('synthetic', 9236, package)
        answer = type('Answer', (), {'stdout': '1234', 'returncode': 0})()
        with patch.object(device, 'adb', return_value=answer) as adb:
            with patch.object(probe.urllib.request, 'urlopen', return_value=io.BytesIO(b'[]')):
                self.assertEqual([], device.targets())
        self.assertEqual(('shell', 'pidof', package), adb.call_args_list[0].args)
        self.assertEqual(('forward', 'tcp:9236', 'localabstract:webview_devtools_remote_1234'), adb.call_args_list[1].args)

    def test_invalid_package_cannot_escape_remote_directory_or_change_runner(self):
        for package in ['com.example/../private', 'com.example;command', 'com.example other', 'single', '.com.example', 'com..example']:
            with self.subTest(package=package):
                with self.assertRaises(probe.DiagnosticError) as caught:
                    probe.Device('synthetic', 9236, package)
                self.assertEqual('invalid_package', caught.exception.reason)

    def test_package_list_cases_does_not_contact_any_device(self):
        output = io.StringIO()
        with patch.object(probe.subprocess, 'run', side_effect=AssertionError('must not contact device')):
            with contextlib.redirect_stdout(output):
                code = probe.main(['--serial', 'synthetic', '--package', 'com.takeruf.nagi.compatprobe', '--list-cases', 'fixture-desktop'])
        self.assertEqual(0, code)
        self.assertEqual('fixture-desktop\n', output.getvalue())


class LocalFontMatrix(unittest.TestCase):
    def test_native_sans_requires_plain_and_uses_space_free_remote_key(self):
        device = probe.Device('synthetic', 9236)
        device.native_sans = 'Noto Sans CJK JP'
        with self.assertRaises(probe.DiagnosticError):
            probe.instrumentation_command(device, 'fixture-desktop')
        device.plain_webview = True
        command = probe.instrumentation_command(device, 'fixture-desktop')
        self.assertEqual(['-e', 'fontNativeSans', 'jp-family'], command[-4:-1])
        self.assertNotIn('Noto Sans CJK JP', command)

    def test_plain_webview_extra_is_added_only_for_fixture(self):
        device = probe.Device('synthetic', 9236, 'com.takeruf.nagi.compatprobe')
        device.plain_webview = True
        command = probe.instrumentation_command(device, 'fixture-desktop')
        self.assertEqual(['-e', 'fontPlain', 'true'], command[-4:-1])
        with self.assertRaises(probe.DiagnosticError):
            probe.instrumentation_command(device, 'chatgpt-desktop')

    def test_default_control_is_rejected_for_public_case_before_contact(self):
        class Client:
            def evaluate(self, *args): raise AssertionError('Must not inspect public document')
            def call(self, *args): raise AssertionError('Must not change public document')
        with self.assertRaises(probe.DiagnosticError):
            probe.apply_generic_sans_control(Client(), 'chatgpt-desktop', 'sans-serif')

    def test_default_control_requires_loopback_even_for_fixture_label(self):
        class Client:
            def evaluate(self, *args): return 'chatgpt.com'
            def call(self, *args): raise AssertionError('Must not change public document')
        with self.assertRaises(probe.DiagnosticError):
            probe.apply_generic_sans_control(Client(), 'fixture-desktop', 'sans-serif')

    def test_default_control_changes_only_sans_not_serif_or_language(self):
        from unittest.mock import Mock
        client = Mock()
        client.evaluate.return_value = '127.0.0.1'
        probe.apply_generic_sans_control(client, 'fixture-desktop', 'Noto Sans CJK JP')
        client.call.assert_called_once_with('Page.setFontFamilies', {'fontFamilies': {'sansSerif': 'Noto Sans CJK JP'}})

    def test_matrix_stack_rejects_incomplete_or_nonobject_source(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'source.json'
            for value in [[], {'status': 'in_progress', 'nodes': []}, {'status': 'complete', 'case': 'chatgpt-desktop', 'nodes': [None]}]:
                path.write_text(json.dumps(value))
                with self.assertRaises(probe.DiagnosticError): probe.read_matrix_stack(path)

    def test_matrix_stack_copies_only_completed_body_font_family(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'source.json'
            path.write_text(json.dumps({'status': 'complete', 'case': 'chatgpt-desktop', 'nodes': [
                {'selector': '#font-probe-body', 'style': {'properties': {'font-family': 'Arial, sans-serif'}}}]}))
            self.assertEqual('Arial, sans-serif', probe.read_matrix_stack(path))


if __name__ == '__main__':
    unittest.main()
