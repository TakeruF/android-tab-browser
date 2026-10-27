/* Text nodes only: preserve elements, event handlers, URLs, form values and page layout. */
(function () {
    const excluded = 'script,style,noscript,template,input,textarea,select,option,pre,code,kbd,samp,svg,math,canvas,iframe,object,embed,[translate="no"],.notranslate,[hidden],[aria-hidden="true"]';
    return function (token, target) {
        if (window.__nagiTranslation) window.__nagiTranslation.restore();
        const records = new Map(), byNode = new WeakMap(), queue = [];
        let nextId = 0, characters = 0, stopped = false, limited = false;
        const originalLanguage = document.documentElement.getAttribute('lang');
        function eligible(node) {
            const parent = node.parentElement;
            if (!node.isConnected || !parent || parent.closest(excluded) || parent.isContentEditable) return false;
            // Hidden ancestors are excluded too, without measuring every text node's layout.
            for (let e = parent; e; e = e.parentElement) {
                const s = getComputedStyle(e);
                if (s.display === 'none' || s.visibility === 'hidden' || s.visibility === 'collapse') return false;
            }
            return /\p{L}/u.test(node.data);
        }
        function enqueue(node) {
            if (!eligible(node)) return;
            let record = byNode.get(node);
            if (record && (node.data === record.translated || (record.translated === null && node.data === record.original))) return;
            if (!record) {
                if (records.size >= 10000 || characters + node.data.length > 2000000 || node.data.length > 32000) { limited = true; return; }
                record = { id: ++nextId, node, original: node.data, translated: null, queued: false };
                records.set(record.id, record); byNode.set(node, record); characters += node.data.length;
            } else {
                characters += node.data.length - record.original.length;
                if (characters > 2000000 || node.data.length > 32000) { limited = true; return; }
                record.original = node.data; record.translated = null;
            }
            if (!record.queued) { record.queued = true; queue.push(record.id); }
        }
        function scan(root) {
            if (root.nodeType === Node.TEXT_NODE) { enqueue(root); return; }
            if (root.nodeType !== Node.ELEMENT_NODE || root.closest(excluded)) return;
            const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
            let node;
            while ((node = walker.nextNode())) enqueue(node);
        }
        function mutations(changes) {
            for (const change of changes) {
                if (change.type === 'characterData') enqueue(change.target);
                else for (const node of change.addedNodes) scan(node);
            }
        }
        const observer = new MutationObserver(mutations);
        scan(document.body);
        observer.observe(document.body, { subtree: true, childList: true, characterData: true });
        return window.__nagiTranslation = {
            token,
            setTarget(language) { target = language; return true; },
            sample() { return Array.from(records.values()).slice(0, 80).map(r => r.original).join('\n').slice(0, 4000); },
            take() {
                mutations(observer.takeRecords());
                const items = []; let size = 0;
                while (queue.length && items.length < 16 && size < 16000) {
                    const r = records.get(queue.shift()); r.queued = false;
                    if (!eligible(r.node) || r.node.data !== r.original) continue;
                    items.push({ id: r.id, text: r.original }); size += r.original.length;
                }
                return { items, limited };
            },
            apply(items) {
                if (stopped) return 0;
                mutations(observer.takeRecords());
                let applied = 0;
                for (const item of items) {
                    const r = records.get(item.id);
                    // A website may update a node while the native translator is working.
                    if (!r || r.original !== item.original || r.node.data !== item.original || !eligible(r.node)) continue;
                    r.translated = item.text; r.node.data = item.text; applied++;
                }
                if (applied) document.documentElement.lang = target;
                observer.takeRecords(); // Do not enqueue our own replacements.
                return applied;
            },
            restore() {
                stopped = true; observer.disconnect();
                for (const r of records.values()) {
                    if (r.node.isConnected && r.translated !== null && r.node.data === r.translated) r.node.data = r.original;
                }
                records.clear(); queue.length = 0;
                if (document.documentElement.getAttribute('lang') === target) {
                    if (originalLanguage === null) document.documentElement.removeAttribute('lang');
                    else document.documentElement.lang = originalLanguage;
                }
                if (window.__nagiTranslation === this) delete window.__nagiTranslation;
                return true;
            }
        };
    };
})()
