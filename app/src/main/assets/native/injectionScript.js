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

(() => {
    const buttonId = 'zadflix-library-scan-button';
    const statusId = 'zadflix-library-scan-status';
    const scanTaskKey = 'RefreshLibrary';
    const pollIntervalMs = 1000;
    const scanTimeoutMs = 10 * 60 * 1000;

    let adminCheckInFlight = false;
    let nextAdminCheckAt = 0;
    let scanInFlight = false;
    let statusTimer;

    const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));

    function getApiClient() {
        return window.ServerConnections?.currentApiClient?.() ?? window.ApiClient;
    }

    function getStatusElement() {
        let status = document.getElementById(statusId);
        if (status) return status;

        status = document.createElement('div');
        status.id = statusId;
        status.className = 'zadflix-library-scan-status';
        status.setAttribute('role', 'status');
        status.setAttribute('aria-live', 'polite');
        document.body.appendChild(status);
        return status;
    }

    function showStatus(message, state = 'progress', dismissAfterMs = 0) {
        window.clearTimeout(statusTimer);
        const status = getStatusElement();
        status.textContent = message;
        status.dataset.state = state;
        status.classList.add('visible');

        if (dismissAfterMs > 0) {
            statusTimer = window.setTimeout(() => status.classList.remove('visible'), dismissAfterMs);
        }
    }

    function setButtonState(button, state, label) {
        button.dataset.state = state;
        button.disabled = state === 'scanning' || state === 'success';
        button.title = label;
        button.setAttribute('aria-label', label);
    }

    function refreshHome() {
        if (window.Dashboard?.navigate) {
            window.Dashboard.navigate('home');
        } else if (window.Emby?.Page?.show) {
            window.Emby.Page.show('home');
        } else {
            window.location.reload();
        }
    }

    async function scanLibrary(button) {
        if (scanInFlight) return;

        scanInFlight = true;
        setButtonState(button, 'scanning', 'Scanning library');
        showStatus('Scanning library for new movies and shows…');

        try {
            const apiClient = getApiClient();
            if (!apiClient) throw new Error('The server connection is not ready.');

            const user = await apiClient.getCurrentUser();
            if (user?.Policy?.IsAdministrator !== true) {
                button.remove();
                throw new Error('Only a server administrator can scan the library.');
            }

            const tasks = await apiClient.getScheduledTasks({ isHidden: false });
            let task = tasks.find(candidate => candidate.Key === scanTaskKey);
            if (!task) throw new Error('The library scan task is unavailable.');

            const previousEndTime = task.LastExecutionResult?.EndTimeUtc;
            let sawActiveScan = task.State !== 'Idle';

            if (!sawActiveScan) {
                await apiClient.startScheduledTask(task.Id);
            }

            const startedAt = Date.now();
            while (true) {
                await delay(pollIntervalMs);
                task = await apiClient.getScheduledTask(task.Id);

                if (task.State === 'Running' || task.State === 'Cancelling') {
                    sawActiveScan = true;
                }

                const progress = Number(task.CurrentProgressPercentage);
                if (Number.isFinite(progress)) {
                    const roundedProgress = Math.max(0, Math.min(100, Math.round(progress)));
                    const progressLabel = `Scanning library: ${roundedProgress}%`;
                    setButtonState(button, 'scanning', progressLabel);
                    showStatus(progressLabel);
                }

                const resultEndTime = task.LastExecutionResult?.EndTimeUtc;
                const producedNewResult = Boolean(resultEndTime && resultEndTime !== previousEndTime);
                if (task.State === 'Idle' && (sawActiveScan || producedNewResult)) break;

                if (Date.now() - startedAt > scanTimeoutMs) {
                    throw new Error('The scan is still running. Check again in a few minutes.');
                }
            }

            const resultStatus = task.LastExecutionResult?.Status;
            if (resultStatus && resultStatus !== 'Completed') {
                throw new Error(`Library scan ended with status: ${resultStatus}.`);
            }

            setButtonState(button, 'success', 'Library updated');
            showStatus('Library updated. New media is ready.', 'success', 3500);
            window.setTimeout(refreshHome, 500);
        } catch (error) {
            console.error('Zadflix library scan failed', error);
            if (button.isConnected) setButtonState(button, 'error', 'Scan library again');
            showStatus(error?.message || 'Library scan failed. Try again.', 'error', 6000);
        } finally {
            scanInFlight = false;
            if (button.isConnected) {
                window.setTimeout(() => setButtonState(button, 'idle', 'Scan library for new media'), 2500);
            }
        }
    }

    async function ensureScanButton() {
        if (document.getElementById(buttonId) || adminCheckInFlight || Date.now() < nextAdminCheckAt) return;

        const searchButton = document.querySelector('.skinHeader .headerSearchButton');
        const apiClient = getApiClient();
        if (!searchButton || !apiClient?.getCurrentUser) return;

        adminCheckInFlight = true;
        try {
            const user = await apiClient.getCurrentUser();
            if (user?.Policy?.IsAdministrator !== true) {
                nextAdminCheckAt = Date.now() + 10000;
                return;
            }

            if (document.getElementById(buttonId) || !searchButton.isConnected) return;

            const button = document.createElement('button');
            button.id = buttonId;
            button.className = 'headerButton headerButtonRight paper-icon-button-light zadflix-library-scan-button';
            button.type = 'button';
            button.innerHTML = '<span class="material-icons refresh" aria-hidden="true"></span>';
            setButtonState(button, 'idle', 'Scan library for new media');
            button.addEventListener('click', () => scanLibrary(button));
            searchButton.parentNode.insertBefore(button, searchButton);
        } catch (error) {
            nextAdminCheckAt = Date.now() + 10000;
            console.debug('Zadflix scan control is waiting for an authenticated administrator.', error);
        } finally {
            adminCheckInFlight = false;
        }
    }

    const observer = new MutationObserver(ensureScanButton);
    observer.observe(document.documentElement, { childList: true, subtree: true });
    window.addEventListener('hashchange', ensureScanButton);
    window.setTimeout(ensureScanButton, 500);
    window.setInterval(ensureScanButton, 2000);
})();
