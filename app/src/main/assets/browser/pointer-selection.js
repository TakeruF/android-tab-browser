(() => {
  if (window.__nagiPointerSelection || !window.nagiSelection) return;
  window.__nagiPointerSelection = true;
  let composing = false;
  document.addEventListener('compositionstart', () => { composing = true; }, true);
  document.addEventListener('compositionend', () => { composing = false; }, true);
  document.addEventListener('pointerup', event => {
    if (!event.isTrusted || event.pointerType !== 'mouse' || event.button !== 0) return;
    const pointer = { x: event.clientX, y: event.clientY };
    // Selection is committed by the browser's default pointer-up processing.
    setTimeout(() => {
      let mirror;
      let point;
      try {
        let editor = document.activeElement;
        while (editor && editor.shadowRoot && editor.shadowRoot.activeElement) editor = editor.shadowRoot.activeElement;
        let rects = [];
        let clip = { left: 0, top: 0, right: innerWidth, bottom: innerHeight };
        if (!composing && editor && /^(INPUT|TEXTAREA)$/.test(editor.tagName)) {
          if (editor.type !== 'password' && editor.selectionStart != null && editor.selectionStart !== editor.selectionEnd) {
            // Measure the native editor's selected characters locally. No text is
            // sent to Android, and the real editor/selection/focus is untouched.
            const bounds = editor.getBoundingClientRect();
            clip = bounds;
            mirror = document.createElement('div');
            const style = getComputedStyle(editor);
            for (const key of ['fontFamily', 'fontSize', 'fontWeight', 'fontStyle', 'fontVariant', 'letterSpacing',
              'lineHeight', 'textAlign', 'textTransform', 'textIndent', 'direction', 'wordSpacing', 'tabSize',
              'boxSizing', 'paddingTop', 'paddingRight', 'paddingBottom', 'paddingLeft',
              'borderTopWidth', 'borderRightWidth', 'borderBottomWidth', 'borderLeftWidth', 'borderStyle']) mirror.style[key] = style[key];
            Object.assign(mirror.style, { position: 'fixed', visibility: 'hidden', pointerEvents: 'none',
              margin: '0', left: `${bounds.left}px`, top: `${bounds.top}px`, width: `${bounds.width}px`,
              height: `${bounds.height}px`, overflow: 'hidden', whiteSpace: editor.tagName === 'INPUT' ? 'pre' : 'pre-wrap',
              overflowWrap: 'break-word' });
            const text = document.createTextNode(editor.value);
            mirror.appendChild(text);
            document.documentElement.appendChild(mirror);
            mirror.scrollLeft = editor.scrollLeft;
            mirror.scrollTop = editor.scrollTop;
            const range = document.createRange();
            range.setStart(text, editor.selectionStart);
            range.setEnd(text, editor.selectionEnd);
            rects = Array.from(range.getClientRects());
          }
        } else if (!composing) {
          const selection = getSelection();
          if (selection && !selection.isCollapsed && selection.rangeCount) rects = Array.from(selection.getRangeAt(0).getClientRects());
        }
        let best = Infinity;
        for (const rect of rects) {
          const left = Math.max(rect.left, clip.left, 0), right = Math.min(rect.right, clip.right, innerWidth);
          const top = Math.max(rect.top, clip.top, 0), bottom = Math.min(rect.bottom, clip.bottom, innerHeight);
          if (right - left < 2 || bottom - top < 2) continue;
          const x = Math.max(left + 1, Math.min(pointer.x, right - 1));
          const y = Math.max(top + 1, Math.min(pointer.y, bottom - 1));
          const distance = Math.hypot(x - pointer.x, y - pointer.y);
          if (distance < best) { best = distance; point = { x, y }; }
        }
      } finally {
        if (mirror) mirror.remove();
      }
      window.nagiSelection.postMessage(JSON.stringify(point
        ? { selected: true, dx: point.x - pointer.x, dy: point.y - pointer.y }
        : { selected: false }));
    }, 0);
  }, true);
})();
