(() => {
  if (window.top !== window || Object.prototype.hasOwnProperty.call(window, '__nagiHumanVerification')) return;
  let failed = false;
  Object.defineProperty(window, '__nagiHumanVerification', { value: (reset = false) => {
    if (reset) failed = false;
    return failed;
  } });
  window.addEventListener('message', event => {
    // Observe the provider's notification, never the response token or form fields.
    // Synthetic events and similarly named hosts must not produce a browser notice.
    if (!event.isTrusted || event.origin !== 'https://challenges.cloudflare.com') return;
    const data = event.data;
    if (!data || typeof data !== 'object' || Array.isArray(data) || data.source !== 'cloudflare-challenge') return;
    if (data.event === 'fail') failed = true;
    else if (data.event === 'complete' || data.event === 'refreshRequest') failed = false;
  });
})();
