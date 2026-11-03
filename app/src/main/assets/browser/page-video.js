(() => {
  if (window.__nagiVideo || typeof nagiVideo === 'undefined') return;
  const frame = Math.random().toString(36).slice(2);
  let selected = null, surface = null, presentationStyle = null, savedScroll = null, surfaceSession = null;
  let savedTargetStyle = null, savedTargetControls = null;
  const marked = [];
  const youtube = /(^|\.)youtube\.com$/.test(location.hostname);
  const suppliedPosters = new WeakMap();
  // Playback alone is not viewing intent: landing pages often loop muted demos.
  // Track intent per video so unrelated page interactions do not claim autoplay.
  const requestedVideos = new WeakSet();
  document.addEventListener('pointerdown', event => {
    if (event.isTrusted && event.target?.tagName === 'VIDEO') requestedVideos.add(event.target);
  }, true);
  document.addEventListener('play', event => {
    const v = event.target;
    if (v?.tagName === 'VIDEO' && navigator.userActivation?.isActive && (!v.autoplay || v.controls)) {
      requestedVideos.add(v);
    }
  }, true);
  document.addEventListener('emptied', event => requestedVideos.delete(event.target), true);
  const restoreChannelPosters = () => {
    if (!youtube) return;
    // YouTube leaves the channel trailer unstarted while its playlist miniplayer plays.
    // Without an HTML poster, WebView paints its large gray fallback over the trailer.
    document.querySelectorAll('ytd-channel-video-player-renderer .html5-video-player').forEach(player => {
      try {
        const v = player.querySelector('video');
        if (!v) return;
        const response = player.getPlayerResponse?.();
        const id = response?.videoDetails?.videoId;
        const expected = player.closest('ytd-channel-video-player-renderer')?.data?.videoId;
        const previous = suppliedPosters.get(v);
        if (previous && (previous.id !== id || (expected && expected !== id))) {
          if (v.getAttribute('poster') === previous.url) v.removeAttribute('poster');
          suppliedPosters.delete(v);
        }
        if (!id || (expected && expected !== id) || !v.paused || v.currentTime !== 0 || v.getAttribute('poster')) return;
        const thumbnails = response.videoDetails.thumbnail?.thumbnails || [];
        const thumbnail = [...thumbnails].filter(t => {
          const url = new URL(t.url);
          return url.protocol === 'https:' && ['i.ytimg.com', 'img.youtube.com'].includes(url.hostname);
        }).sort((a, b) => b.width - a.width)[0];
        if (!thumbnail) return;
        v.setAttribute('poster', thumbnail.url);
        suppliedPosters.set(v, {id, url: thumbnail.url});
      } catch (_) {} // Site internals may change; ordinary playback must keep working.
    });
  };
  const videos = () => Array.from(document.querySelectorAll('video'));
  const candidate = () => {
    if (selected?.isConnected) return selected;
    const full = document.fullscreenElement;
    if (full) return full.tagName === 'VIDEO' ? full : full.querySelector('video');
    return videos().filter(v => !v.paused && !v.ended && v.readyState >= 2).sort((a, b) => {
      const x = a.getBoundingClientRect(), y = b.getBoundingClientRect();
      return y.width * y.height - x.width * x.height;
    })[0] || null;
  };
  const send = (type, v = candidate()) => {
    nagiVideo.postMessage(JSON.stringify({type, frame, hasVideo: !!v,
      playing: !!v && !v.paused && !v.ended, width: v?.videoWidth || 16, height: v?.videoHeight || 9,
      currentTime: Number.isFinite(v?.currentTime) ? v.currentTime : 0,
      duration: Number.isFinite(v?.duration) ? v.duration : 0,
      autoPopoutAllowed: !!v && !v.disablePictureInPicture && requestedVideos.has(v),
      fullscreen: !!document.fullscreenElement && !!v}));
  };
  const mark = (element, name) => {
    marked.push([element, name, element.getAttribute(name)]);
    element.setAttribute(name, '');
  };
  const notifyParent = action => {
    if (parent !== window) parent.postMessage({nagiVideoSurface: action, session: surfaceSession}, '*');
  };
  const fitSurface = () => {
    if (!surface || !presentationStyle?.sheet) return;
    // WebView can pan its visual viewport while resizing/normalizing desktop-page zoom.
    // Fixed 0/0 plus 100vw/100vh then crops the image even with object-fit:contain.
    const viewport = window.visualViewport;
    const bounds = {
      left: viewport?.offsetLeft || 0, top: viewport?.offsetTop || 0,
      width: viewport?.width || document.documentElement.clientWidth,
      height: viewport?.height || document.documentElement.clientHeight
    };
    const rules = presentationStyle.sheet.cssRules;
    const rule = rules[rules.length - 1];
    for (const [name, value] of Object.entries(bounds)) {
      if (!Number.isFinite(value) || ((name === 'width' || name === 'height') && value <= 0)) continue;
      const pixels = value + 'px';
      rule.style.setProperty(name, pixels, 'important');
      surface.style.setProperty(name, pixels, 'important');
    }
  };
  const restore = () => {
    if (surface && savedTargetStyle !== null) {
      if (savedTargetStyle) surface.setAttribute('style', savedTargetStyle);
      else surface.removeAttribute('style');
    }
    if (surface?.tagName === 'VIDEO') {
      if (savedTargetControls === null) surface.removeAttribute('controls');
      else surface.setAttribute('controls', savedTargetControls);
    }
    for (const [element, name, previous] of marked.splice(0)) {
      if (previous === null) element.removeAttribute(name); else element.setAttribute(name, previous);
    }
    presentationStyle?.remove(); presentationStyle = null;
    surface = null; selected = null; savedTargetStyle = null; savedTargetControls = null;
    if (savedScroll) scrollTo(savedScroll.x, savedScroll.y);
    savedScroll = null;
    notifyParent('restore');
  };
  const present = target => {
    if (!target?.isConnected || surface === target) return;
    restore();
    surface = target;
    savedScroll = {x: scrollX, y: scrollY};
    savedTargetStyle = target.getAttribute('style') || '';
    savedTargetControls = target.getAttribute('controls');
    // The app supplies popup controls. Native HTML controls otherwise react to mouse corners
    // (for example, the bottom-left play button) even while the page UI is hidden.
    if (target.tagName === 'VIDEO') target.removeAttribute('controls');
    mark(document.documentElement, 'data-nagi-video-surface');
    for (let e = target.parentElement; e; e = e.parentElement) mark(e, 'data-nagi-video-ancestor');
    mark(target, 'data-nagi-video-target');
    const targetStyles = {position:'fixed',left:'0',top:'0',width:'100vw',height:'100vh',
      'max-width':'none','max-height':'none',margin:'0',padding:'0',border:'0',transform:'none',
      visibility:'visible','object-fit':'contain','object-position':'50% 50%','box-sizing':'border-box',
      'z-index':'2147483647',background:'black'};
    presentationStyle = document.createElement('style');
    presentationStyle.textContent = `
      html[data-nagi-video-surface],html[data-nagi-video-surface] body {background:black!important;overflow:hidden!important;}
      html[data-nagi-video-surface] body * {visibility:hidden!important;}
      [data-nagi-video-ancestor] {transform:none!important;filter:none!important;perspective:none!important;contain:none!important;overflow:visible!important;}
      html[data-nagi-video-surface] [data-nagi-video-target] {${Object.entries(targetStyles).map(([k,v]) => k + ':' + v + '!important').join(';')}}
    `;
    document.documentElement.append(presentationStyle);
    for (const [name, value] of Object.entries(targetStyles)) {
      target.style.setProperty(name, value, 'important');
    }
    fitSurface();
    notifyParent('present');
  };
  const control = action => {
    if (action === 'restore') { restore(); surfaceSession = null; return; }
    const v = candidate();
    if (!v) return;
    if (action === 'present') {
      if (v.paused || v.ended) return;
      present(v); selected = v; send('state', v);
    }
    if (action === 'pause') v.pause();
    if (action === 'play') v.play().catch(() => {});
  };
  window.__nagiVideo = { control };
  nagiVideo.onmessage = event => {
    try {
      const message = JSON.parse(event.data);
      if (message.type === 'prepare-surface' && /^[a-f0-9]{32}$/.test(message.session)) surfaceSession = message.session;
      if (message.type === 'control') control(message.action);
    } catch (_) {}
  };
  // Only a direct child can ask us to expose its frame. No cross-origin DOM is read.
  addEventListener('message', event => {
    if (!surfaceSession || event.data?.session !== surfaceSession ||
        !['present', 'restore'].includes(event.data?.nagiVideoSurface)) return;
    const child = Array.from(document.querySelectorAll('iframe')).find(f => f.contentWindow === event.source);
    if (!child) return;
    if (event.data.nagiVideoSurface === 'present') present(child);
    else if (surface === child) restore();
  });
  const tick = () => {
    restoreChannelPosters();
    if (surface && !surface.isConnected) { restore(); send('state', null); return; }
    fitSurface();
    send('state');
  };
  document.addEventListener('fullscreenchange', () => {
    if (document.fullscreenElement) {
      selected = candidate();
      if (selected) requestedVideos.add(selected);
    }
    else if (!surface) selected = null;
    tick();
  });
  ['play', 'pause', 'ended', 'loadedmetadata', 'resize'].forEach(type => document.addEventListener(type, tick, true));
  addEventListener('resize', tick);
  window.visualViewport?.addEventListener('resize', fitSurface);
  window.visualViewport?.addEventListener('scroll', fitSurface);
  setInterval(tick, 750);
  send('ready');
})();
