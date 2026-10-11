// Collapse only empty branches around positively identified blocked advertisements.
// No host names, layout class names, size thresholds, or downloaded scriptlets.
(() => {
  if (window.__nagiAdSlots) return;
  const collapseAttr = 'data-nagi-adblock-collapse';
  const resourceAttr = 'data-nagi-adblock-resource';
  const ignored = new Set(['SCRIPT', 'STYLE', 'LINK', 'META', 'TEMPLATE', 'NOSCRIPT']);
  const boundaries = 'html,head,body,main,article,header,nav,footer,form,[role="main"],[role="navigation"],[role="banner"]';
  const content = 'input,textarea,select,button,a[href],summary,video,audio,canvas,svg,img,picture,iframe,object,embed,[contenteditable]:not([contenteditable="false"]),[role]';
  const marks = new Map();
  const resourceMarks = new Map();
  let query = '', selectorKey = '', urls = new Set(), observer = null, timer = null;
  let active = false;

  function setMarks(previous, next, attribute) {
    for (const [element, value] of previous) {
      if (next.has(element)) continue;
      if (element.getAttribute(attribute) === '1') {
        if (value === null) element.removeAttribute(attribute);
        else element.setAttribute(attribute, value);
      }
      previous.delete(element);
    }
    for (const element of next) {
      if (!previous.has(element)) previous.set(element, element.getAttribute(attribute));
      if (element.getAttribute(attribute) !== '1') element.setAttribute(attribute, '1');
    }
  }

  function normalizedUrl(value) {
    try { const url = new URL(value, document.baseURI); url.hash = ''; return url.href; }
    catch (_) { return ''; }
  }

  function hasContent(node, evidence, branches) {
    if (node.nodeType === Node.TEXT_NODE) return !!node.textContent.trim();
    if (node.nodeType !== Node.ELEMENT_NODE) return false;
    if (evidence.has(node) || ignored.has(node.tagName)) return false;
    // Preserve controls/media even when currently hidden or still loading.
    if (node.matches(content)) return true;
    const style = getComputedStyle(node);
    if (style.backgroundImage !== 'none') return true;
    for (const pseudo of ['::before', '::after']) {
      const value = getComputedStyle(node, pseudo).content;
      if (value && !['none', 'normal', '""', "''"].includes(value)) return true;
    }
    for (const child of node.childNodes) if (hasContent(child, evidence, branches)) return true;
    // An unrelated empty spacer or CSS illustration is not an advertisement.
    // Do not use our own temporary display:none as evidence of emptiness.
    if (!branches.has(node)) {
      const rect = node.getBoundingClientRect();
      if ((rect.width > 0 && rect.height > 0) || parseFloat(style.height) > 0 ||
          parseFloat(style.minHeight) > 0) return true;
    }
    return false;
  }

  function reconcile() {
    timer = null;
    if (!active) return;
    const evidence = new Set(query ? document.querySelectorAll(query) : []);
    const resources = new Set();
    if (urls.size) for (const node of document.querySelectorAll('img,iframe,object,embed')) {
      const value = node.currentSrc || node.getAttribute('src') || node.getAttribute('data');
      if (!value) continue;
      const url = normalizedUrl(value);
      if (urls.has(url)) { resources.add(node); evidence.add(node); }
    }
    const branches = new Set();
    for (const ad of evidence) for (let node = ad.parentElement; node; node = node.parentElement) branches.add(node);
    const next = new Set();
    for (const ad of evidence) {
      for (let parent = ad.parentElement; parent; parent = parent.parentElement) {
        if (parent.matches(boundaries) || hasContent(parent, evidence, branches)) break;
        next.add(parent);
      }
    }
    setMarks(resourceMarks, resources, resourceAttr);
    setMarks(marks, next, collapseAttr);
  }

  function schedule() {
    if (active && timer === null) timer = setTimeout(reconcile, 50);
  }

  document.addEventListener('load', schedule, true);
  document.addEventListener('error', schedule, true);
  window.addEventListener('resize', schedule);

  window.__nagiAdSlots = {
    update(selectors, blockedUrls, enabled) {
      active = enabled;
      if (!active) {
        if (timer !== null) clearTimeout(timer);
        timer = null;
        if (observer) observer.disconnect();
        observer = null;
        setMarks(marks, new Set(), collapseAttr);
        setMarks(resourceMarks, new Set(), resourceAttr);
        query = ''; selectorKey = ''; urls.clear();
        return;
      }
      const key = JSON.stringify(selectors);
      if (key !== selectorKey) {
        const probe = document.createDocumentFragment();
        query = selectors.filter(selector => {
          try { probe.querySelector(selector); return true; } catch (_) { return false; }
        }).join(',');
        selectorKey = key;
      }
      urls = new Set(blockedUrls.map(normalizedUrl));
      if (!observer) {
        observer = new MutationObserver(changes => {
          if (changes.some(change => {
            if (change.type === 'attributes' && [collapseAttr, resourceAttr].includes(change.attributeName)) return false;
            return change.target.id !== '__nagi_adblock_style';
          })) schedule();
        });
        observer.observe(document.documentElement, {childList:true, subtree:true, attributes:true, characterData:true});
      }
      if (timer !== null) clearTimeout(timer);
      reconcile();
    }
  };
})();
