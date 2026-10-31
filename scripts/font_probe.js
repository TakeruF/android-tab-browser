// Diagnostic-only helpers. Read computed font styles and geometry, never page text or input values.
(() => {
  const EXTRA = ['line-height', 'letter-spacing', 'word-spacing', 'text-rendering',
    'writing-mode', 'text-orientation', 'direction', 'text-transform', 'text-size-adjust', '-webkit-text-size-adjust', '-webkit-locale'];
  const JAPANESE = '日本語漢字ひらがなカタカナ';
  let capture = null;

  function guard(nonce, id) {
    if (window.__nagiFontProbeNonce !== nonce || (id && window.__nagiFontProbeCaptureId !== id))
      throw new Error('Diagnostic document changed');
  }
  function effectiveLang(element) {
    return element.closest('[lang]')?.getAttribute('lang') ?? document.documentElement.lang ?? '';
  }
  function snapshot(element) {
    const cs = getComputedStyle(element);
    const names = [...new Set([...Array.from(cs).filter(name => name.startsWith('font-')), ...EXTRA])].sort();
    const properties = {};
    for (const name of names) {
      const value = cs.getPropertyValue(name);
      if (name === '-webkit-locale' && value && !/^(auto|"[A-Za-z0-9-]{0,35}")$/.test(value))
        throw new Error('Unsupported diagnostic locale');
      if (value) properties[name] = value;
    }
    const lang = effectiveLang(element);
    return {properties, lang: /^[A-Za-z0-9-]{0,35}$/.test(lang) ? lang : null};
  }
  function visible(selector) {
    return [...document.querySelectorAll(selector)].find(e => e.getClientRects().length &&
      getComputedStyle(e).visibility !== 'hidden' && getComputedStyle(e).visibility !== 'collapse');
  }
  async function waitForFonts(fonts, milliseconds) {
    let timer;
    try {
      await Promise.race([fonts.ready, new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error('Font readiness timed out')), milliseconds);
      })]);
      if (fonts.status !== 'loaded') throw new Error('Font readiness incomplete');
      return true;
    } finally { clearTimeout(timer); }
  }
  function begin(nonce, id, label) {
    guard(nonce);
    if (!document.body || !/^[a-zA-Z0-9-]+$/.test(id)) throw new Error('Missing diagnostic document');
    window.__nagiFontProbeCaptureId = id;
    capture = {id, nonce, label, host: null, expected: []};
    const addReference = selector => capture.expected.push({selector, root: 'document'});
    if (label.startsWith('fixture')) {
      for (let i = 0; i < 7; i++) addReference('#font-' + i);
    } else if (label.startsWith('controls')) {
      for (const name of ['baseline-sans','baseline-serif','missing-sans','missing-serif',
        'invalid-sans','latin-only-latin','latin-only-japanese','rule-latin']) addReference('#control-' + name);
    } else if (label === 'api-controls') {
      addReference('#api-reference');
    } else {
      let sources;
      if (label === 'copy-controls') {
        document.querySelector('#copy-reference-normal').style.fontVariationSettings = '"wght" 650, "wdth" 75';
        document.querySelector('#copy-reference-normal').style.fontOpticalSizing = 'none';
        sources = ['normal','italic','japanese','chinese'].map(name => ({name,
          element: document.querySelector('#copy-reference-' + name),
          text: ['normal','italic'].includes(name) ? 'ABC 123' : JAPANESE}));
        for (const source of sources) addReference('#copy-reference-' + source.name);
      } else {
        sources = [{name: 'body', element: document.body, text: JAPANESE},
          {name: 'button', element: visible('button'), text: JAPANESE}];
        if (label.startsWith('chatgpt')) sources.push({name: 'editor', element: visible('textarea,[contenteditable=true]'), text: JAPANESE});
      }
      if (sources.some(source => !source.element)) throw new Error('Missing font style source');
      const host = document.createElement('div');
      host.id = 'nagi-font-probe-' + id;
      host.style.cssText = 'all:initial!important;position:fixed!important;left:0!important;top:0!important;z-index:2147483647!important;display:block!important;visibility:visible!important;pointer-events:none!important';
      const root = host.attachShadow({mode: 'open'});
      for (const source of sources) {
        const sourceStyle = snapshot(source.element);
        const probe = document.createElement('span');
        probe.id = 'font-probe-' + source.name;
        probe.textContent = source.text;
        probe.lang = effectiveLang(source.element);
        probe.style.cssText = 'display:block!important;color:black!important;background:white!important';
        for (const [name, value] of Object.entries(sourceStyle.properties)) probe.style.setProperty(name, value, 'important');
        root.appendChild(probe);
        capture.expected.push({selector: '#' + probe.id, root: 'shadow', sourceStyle});
        if (label === 'copy-controls') {
          const documentProbe = probe.cloneNode(true);
          documentProbe.id = 'font-probe-document-' + source.name;
          // Same document root, no reset and no explicit locale copy: preserve the HTML lang hint.
          documentProbe.style.removeProperty('-webkit-locale');
          document.body.appendChild(documentProbe);
          capture.expected.push({selector: '#' + documentProbe.id, root: 'document', sourceStyle});
          if (source.name === 'japanese') {
            const resetProbe = probe.cloneNode(true);
            resetProbe.id = 'font-probe-reset-japanese';
            resetProbe.style.cssText = 'all:initial!important;display:block!important';
            for (const [name, value] of Object.entries(sourceStyle.properties))
              if (name !== '-webkit-locale') resetProbe.style.setProperty(name, value, 'important');
            root.appendChild(resetProbe);
            capture.expected.push({selector: '#' + resetProbe.id, root: 'shadow', controlRole: 'intentional-locale-loss'});
            const fixedProbe = resetProbe.cloneNode(true);
            fixedProbe.id = 'font-probe-reset-fixed-japanese';
            fixedProbe.style.setProperty('-webkit-locale', sourceStyle.properties['-webkit-locale'], 'important');
            root.appendChild(fixedProbe);
            capture.expected.push({selector: '#' + fixedProbe.id, root: 'shadow', sourceStyle});
          }
          if (source.name === 'italic') {
            const legacyProbe = document.createElement('span');
            legacyProbe.id = 'font-probe-legacy-italic';
            legacyProbe.textContent = source.text;
            legacyProbe.lang = 'ja';
            legacyProbe.style.cssText = 'display:block!important;font-size:32px!important';
            for (const name of ['font-family', 'font-weight'])
              legacyProbe.style.setProperty(name, sourceStyle.properties[name], 'important');
            root.appendChild(legacyProbe);
            capture.expected.push({selector: '#' + legacyProbe.id, root: 'shadow', controlRole: 'legacy-copy'});
          }
          if (source.name === 'normal') {
            const variationProbe = probe.cloneNode(true);
            variationProbe.id = 'font-probe-variation-lost';
            variationProbe.style.setProperty('font-variation-settings', 'normal', 'important');
            root.appendChild(variationProbe);
            capture.expected.push({selector: '#' + variationProbe.id, root: 'shadow', controlRole: 'intentional-variation-loss'});
          }
        }
      }
      document.body.appendChild(host);
      capture.host = host;
    }
    for (const spec of capture.expected) {
      const node = (spec.root === 'shadow' ? capture.host.shadowRoot : document).querySelector(spec.selector);
      if (!node) throw new Error('Missing synthetic control');
      // Force font selection for the newly added synthetic text before reading fonts.ready.
      node.getBoundingClientRect();
    }
    const category = location.hostname === 'chatgpt.com' ? 'chatgpt' : location.hostname === 'accounts.google.com' ? 'google-accounts' :
      location.hostname === 'docs.google.com' ? 'google-docs' : location.hostname === '127.0.0.1' ? 'loopback' : 'other';
    return {expected: capture.expected, hostId: capture.host?.id ?? null,
      metadata: {originCategory: category, ready: document.readyState, fontStatus: document.fonts.status}};
  }
  function read(nonce, id, selector, root) {
    guard(nonce, id);
    if (!capture || capture.id !== id) throw new Error('Capture missing');
    const spec = capture.expected.find(item => item.selector === selector && item.root === root);
    if (!spec) throw new Error('Unknown diagnostic node');
    const node = (root === 'shadow' ? capture.host.shadowRoot : document).querySelector(selector);
    if (!node) throw new Error('Synthetic node disappeared');
    return snapshot(node);
  }
  function context(nonce, id) {
    return window.__nagiFontProbeNonce === nonce && window.__nagiFontProbeCaptureId === id && !!capture && capture.id === id;
  }
  function matrix(nonce, id, publicStack) {
    guard(nonce, id);
    if (!capture || !capture.label.startsWith('fixture') || location.hostname !== '127.0.0.1' ||
        typeof publicStack !== 'string' || publicStack.length > 2048) throw new Error('Invalid local matrix scope');
    for (const [family, local] of [['NagiMatrixLocalJP', 'Noto Sans CJK JP'], ['NagiMatrixLocalSC', 'Noto Sans CJK SC'],
      ['NagiMatrixLocalJPPS', 'NotoSansCJKJP-Regular'], ['NagiMatrixLocalSCPS', 'NotoSansCJKsc-Regular']])
      document.fonts.add(new FontFace(family, 'local("' + local + '")'));
    const families = [
      ['sans', 'sans-serif'], ['ui-sans', 'ui-sans-serif'], ['system', 'system-ui'], ['arial', 'Arial'],
      ['roboto', 'Roboto'], ['missing-sans', '"NagiMissingFont",sans-serif'], ['serif', 'serif'], ['public-stack', publicStack],
      ['noto-jp', '"Noto Sans CJK JP",sans-serif'], ['noto-sc', '"Noto Sans CJK SC",sans-serif'],
      ['local-jp', 'NagiMatrixLocalJP,sans-serif'], ['local-sc', 'NagiMatrixLocalSC,sans-serif'],
      ['noto-jp-ps', '"NotoSansCJKJP-Regular",sans-serif'], ['noto-sc-ps', '"NotoSansCJKsc-Regular",sans-serif'],
      ['local-jp-ps', 'NagiMatrixLocalJPPS,sans-serif'], ['local-sc-ps', 'NagiMatrixLocalSCPS,sans-serif']
    ];
    const languages = [['ja', 'ja'], ['zh', 'zh-CN'], ['en', 'en-US'], ['unknown', '']];
    const host = document.createElement('div');
    host.id = 'nagi-font-probe-' + id;
    host.lang = '';
    host.style.cssText = 'all:initial!important;position:relative!important;display:block!important';
    const root = host.attachShadow({mode: 'open'});
    function row(languageKey, language, familyKey, family, group, text) {
      const node = document.createElement('span');
      node.id = 'matrix-' + languageKey + '-' + familyKey + '-' + group;
      node.lang = language;
      node.textContent = text;
      node.style.cssText = 'display:block!important;font-size:32px!important;font-weight:400!important;font-style:normal!important';
      node.style.setProperty('font-family', family, 'important');
      root.appendChild(node);
      capture.expected.push({selector: '#' + node.id, root: 'shadow', matrixSpec: {languageKey, language,
        familyKey, textGroup: group, characters: text.length}});
    }
    for (const [languageKey, language] of languages)
      for (const [familyKey, family] of families) row(languageKey, language, familyKey, family, 'all', JAPANESE);
    for (const [languageKey, language] of languages.slice(0, 2))
      for (const [familyKey, family] of families.filter(([key]) => ['ui-sans', 'public-stack'].includes(key))) {
        row(languageKey, language, familyKey, family, 'han', '日本語漢字');
        row(languageKey, language, familyKey, family, 'kana', 'ひらがなカタカナ');
      }
    document.body.appendChild(host);
    capture.host = host;
    for (const node of root.children) node.getBoundingClientRect();
    return {expected: capture.expected, hostId: host.id,
      metadata: {originCategory: 'loopback', ready: document.readyState, fontStatus: document.fonts.status,
        matrixLanguages: languages, matrixFamilies: families, matrixRows: root.children.length}};
  }
  function localeControls(nonce, id) {
    guard(nonce, id);
    if (capture.label !== 'copy-controls') throw new Error('Not a synthetic locale control');
    return capture.expected.map(spec => {
      const e = (spec.root === 'shadow' ? capture.host.shadowRoot : document).querySelector(spec.selector);
      const range = document.createRange();
      range.selectNodeContents(e); // Only fixed synthetic control nodes are accepted by this method.
      const box = range.getBoundingClientRect();
      return {selector: spec.selector, root: spec.root, locale: getComputedStyle(e).getPropertyValue('-webkit-locale'),
        width: box.width, height: box.height};
    });
  }
  Object.defineProperty(window, '__nagiFontProbeApi', {value: Object.freeze({begin, read, context, waitForFonts, localeControls, matrix}), configurable: true});
  return true;
})()
