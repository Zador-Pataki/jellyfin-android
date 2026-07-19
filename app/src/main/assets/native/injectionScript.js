(() => {
    const themeStylesheet = document.createElement('link');
    themeStylesheet.id = 'zadflix-theme';
    themeStylesheet.rel = 'stylesheet';
    themeStylesheet.href = '/native/zadflix-theme.css';
    document.head.appendChild(themeStylesheet);

    const scripts = [
        '/native/nativeshell.js',
        '/native/EventEmitter.js',
        document.currentScript.src.concat('?deferred=true&ts=', Date.now())
    ];
    for (const script of scripts) {
        const scriptElement = document.createElement('script');
        scriptElement.src = script;
        scriptElement.charset = 'utf-8';
        scriptElement.setAttribute('defer', '');
        document.body.appendChild(scriptElement);
    }
    document.currentScript.remove();
})();
