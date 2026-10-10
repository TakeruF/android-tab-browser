(() => {
  if (window.__nagiVideo || typeof nagiVideo === 'undefined') return;
  const frame = Math.random().toString(36).slice(2);
  let enabled = true, label = 'Pop out video', selected = null, button = null, pendingPopup = false;
  const videos = () => Array.from(document.querySelectorAll('video'));
  const candidate = () => {
    if (selected && selected.isConnected) return selected;
    const full = document.fullscreenElement;
    if (full) return full.tagName === 'VIDEO' ? full : full.querySelector('video');
    return videos().filter(v => !v.paused && !v.ended).sort((a, b) => {
      const x = a.getBoundingClientRect(), y = b.getBoundingClientRect();
      return y.width * y.height - x.width * x.height;
    })[0] || null;
  };
  const send = (type, v = candidate()) => {
    nagiVideo.postMessage(JSON.stringify({type, frame, hasVideo: !!v,
      playing: !!v && !v.paused && !v.ended, width: v?.videoWidth || 16, height: v?.videoHeight || 9,
      fullscreen: !!document.fullscreenElement && !!v}));
  };
  const control = action => {
    const v = candidate();
    if (!v) return;
    if (action === 'pause') v.pause();
    if (action === 'play') v.play().catch(() => {});
  };
  window.__nagiVideo = { control };
  nagiVideo.onmessage = event => {
    try {
      const message = JSON.parse(event.data);
      if (message.type === 'config') { enabled = message.enabled; label = message.label; }
      else if (message.type === 'control') control(message.action);
      tick();
    } catch (_) {}
  };
  const popout = async () => {
    const v = candidate();
    if (!enabled || !v) return;
    selected = v;
    pendingPopup = true;
    send('popup', v);
    try {
      // Run directly in the DOM click handler to preserve the browser's user activation.
      await v.requestFullscreen();
    } catch (_) { pendingPopup = false; selected = null; send('popup-failed'); }
  };
  const tick = () => {
    const v = candidate();
    if (v || selected || button) send('state', v);
    const rect = v?.getBoundingClientRect();
    const visible = enabled && !document.fullscreenElement && v && !v.paused && rect &&
      rect.width >= 120 && rect.height >= 70 && rect.bottom > 0 && rect.top < innerHeight;
    if (visible && !button && document.documentElement) {
      const host = document.createElement('div');
      host.setAttribute('data-nagi-video-assistant', '');
      host.style.cssText = 'position:fixed;z-index:2147483647;';
      const shadow = host.attachShadow({mode:'closed'});
      button = document.createElement('button');
      button.style.cssText = 'font:14px sans-serif;background:#202522;color:white;border:1px solid #ffffff88;border-radius:20px;padding:10px 14px;cursor:pointer;box-shadow:0 2px 8px #0006;';
      button.onclick = popout;
      shadow.append(button);
      document.documentElement.append(host);
      button.host = host;
    }
    if (button) {
      button.host.style.display = visible ? 'block' : 'none';
      button.textContent = label;
      button.setAttribute('aria-label', label);
      if (visible) {
        button.host.style.left = Math.max(4, Math.min(innerWidth - button.offsetWidth - 8, rect.right - button.offsetWidth - 8)) + 'px';
        button.host.style.top = Math.max(4, Math.min(innerHeight - 48, rect.top + 8)) + 'px';
      }
    }
  };
  document.addEventListener('fullscreenchange', () => {
    if (!document.fullscreenElement) { selected = null; pendingPopup = false; }
    else { selected = candidate(); if (pendingPopup) send('popup', selected); }
    tick();
  });
  ['play', 'pause', 'ended', 'loadedmetadata', 'resize'].forEach(type => document.addEventListener(type, tick, true));
  addEventListener('scroll', tick, {passive:true});
  addEventListener('resize', tick);
  // Covers dynamically inserted players and cross-origin frames without reading their DOM from the parent.
  setInterval(tick, 750);
  send('ready');
})();
