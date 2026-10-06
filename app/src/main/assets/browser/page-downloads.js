(function () {
  if (window !== window.top || window.__nagiPageDownloads || !window.nagiDownload) return;
  const limit = 32 * 1024 * 1024;
  let pending = null;
  let busy = false;
  document.addEventListener('input', function () { window.__nagiEdited = true; }, true);
  async function capture(url, name) {
    if (busy || pending || url.length > limit * 2 || !/^(blob:|data:)/i.test(url)) return;
    busy = true;
    try {
      // Fetch immediately: many exporters revoke their object URL after anchor.click().
      const blob = await (await fetch(url)).blob();
      if (blob.size > limit) throw new Error('size');
      const id = String(Date.now()) + '-' + Math.random().toString(36).slice(2);
      pending = { id, blob };
      window.nagiDownload.postMessage(JSON.stringify({id, size: blob.size,
        name: (name || 'download').slice(0, 160), mime: blob.type || 'application/octet-stream'}));
    } catch (_) {
      window.nagiDownload.postMessage(JSON.stringify({error: true}));
    } finally { busy = false; }
  }
  window.__nagiPageDownloads = {
    capture,
    isBusy: function () { return busy || pending !== null; },
    read: async function (id, offset) {
      if (!pending || pending.id !== id) throw new Error('expired');
      const bytes = new Uint8Array(await pending.blob.slice(offset, offset + 49152).arrayBuffer());
      let text = '';
      for (let i = 0; i < bytes.length; i++) text += String.fromCharCode(bytes[i]);
      return btoa(text);
    },
    release: function (id) { if (pending && pending.id === id) pending = null; }
  };
  document.addEventListener('click', function (event) {
    const anchor = event.target.closest && event.target.closest('a[download]');
    if (!anchor || !/^(blob:|data:)/i.test(anchor.href)) return;
    if (!event.isTrusted && !(navigator.userActivation && navigator.userActivation.isActive)) return;
    event.preventDefault();
    capture(anchor.href, anchor.download);
  }, true);
})();
