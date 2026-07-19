(() => {
    const startupStyle = document.createElement('style');
    startupStyle.id = 'zadflix-startup-theme';
    startupStyle.textContent = `
        @-webkit-keyframes zadflix-loading-spin {
            from { -webkit-transform: translate(-50%, -50%) rotate(0deg); }
            to { -webkit-transform: translate(-50%, -50%) rotate(360deg); }
        }
        @keyframes zadflix-loading-spin {
            from { transform: translate(-50%, -50%) rotate(0deg); }
            to { transform: translate(-50%, -50%) rotate(360deg); }
        }
        .splashLogo {
            -webkit-animation: zadflix-loading-spin 1s linear infinite !important;
            animation: zadflix-loading-spin 1s linear infinite !important;
            background-image: url('/native/zadflix-loading-face.png') !important;
            transform-origin: center;
        }
    `;
    document.head.appendChild(startupStyle);

    let startupReady = false;
    const showWebapp = () => {
        if (startupReady) return;
        startupReady = true;
        window.ZadflixStartup?.ready();
    };
    const startupLogo = new Image();
    startupLogo.addEventListener('load', showWebapp, { once: true });
    startupLogo.addEventListener('error', () => {
        startupStyle.textContent = '.splashLogo { display: none !important; }';
        showWebapp();
    }, { once: true });
    startupLogo.src = '/native/zadflix-loading-face.png';
    if (startupLogo.complete) {
        if (startupLogo.naturalWidth > 0) {
            showWebapp();
        } else {
            startupStyle.textContent = '.splashLogo { display: none !important; }';
            showWebapp();
        }
    }

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
    const movieCardSelector = '.card[data-id][data-type="Movie"][data-mediatype="Video"]';
    const videoCardSelector = '.card[data-id][data-mediatype="Video"]';
    const downloadButtonSelector = '.zadflix-card-download';
    const pendingDownloadStates = new Set(['QUEUED', 'DOWNLOADING']);
    const absentDownloadStates = new Set(['', 'NONE', 'ABSENT', 'NOT_DOWNLOADED', 'ERROR', 'CANCELLED']);
    const stateRefreshIntervalMs = 5000;
    const stateQueryThrottleMs = 250;
    const pendingDownloadFeedbackTimeoutMs = 30 * 1000;
    const pendingDeletionTimeoutMs = 10 * 1000;
    const downloadWatchTimeoutMs = 12 * 60 * 60 * 1000;

    const downloadStates = new Map();
    const pendingActions = new Map();
    const watchedDownloads = new Map();
    let reconciliationScheduled = false;
    let forceStateRefresh = true;
    let lastStateQueryAt = 0;
    let stateRefreshTimer;

    function movieName(card) {
        return card.querySelector('.textActionButton')?.textContent?.trim()
            || card.querySelector('.cardImageContainer')?.getAttribute('aria-label')
            || 'movie';
    }

    function setButtonIcon(button, iconName) {
        const icon = button.querySelector('.material-icons');
        if (icon) icon.className = `material-icons ${iconName}`;
    }

    function setButtonChecking(button, card) {
        button.dataset.itemId = card.dataset.id;
        button.dataset.action = 'checking';
        button.dataset.state = 'checking';
        button.disabled = true;
        button.title = `Checking download for ${movieName(card)}`;
        button.setAttribute('aria-label', button.title);
        button.setAttribute('aria-busy', 'true');
        setButtonIcon(button, 'downloading');
    }

    function normalizeDownloadState(value) {
        if (value === true) return 'DOWNLOADED';
        if (value === false || value == null) return 'NONE';

        if (typeof value === 'string') return value.toUpperCase();
        if (typeof value !== 'object') return 'NONE';

        const status = value.status ?? value.state ?? value.downloadStatus;
        if (typeof status === 'string') return status.toUpperCase();
        if (value.downloaded === true) return 'DOWNLOADED';
        if (value.present === true || value.exists === true) return 'DOWNLOADED';
        return 'NONE';
    }

    function parseDownloadStates(rawStates, itemIds) {
        let parsedStates = rawStates;
        if (typeof rawStates === 'string') parsedStates = JSON.parse(rawStates);

        const states = new Map(itemIds.map(itemId => [itemId, 'NONE']));
        if (Array.isArray(parsedStates)) {
            parsedStates.forEach(entry => {
                if (typeof entry === 'string') {
                    if (states.has(entry)) states.set(entry, 'DOWNLOADED');
                    return;
                }

                const itemId = entry?.itemId ?? entry?.id;
                if (states.has(itemId)) states.set(itemId, normalizeDownloadState(entry));
            });
        } else if (parsedStates && typeof parsedStates === 'object') {
            itemIds.forEach(itemId => {
                if (Object.prototype.hasOwnProperty.call(parsedStates, itemId)) {
                    states.set(itemId, normalizeDownloadState(parsedStates[itemId]));
                }
            });
        }

        return states;
    }

    function isDownloaded(state) {
        return state === 'DOWNLOADED';
    }

    function isDownloadPending(state) {
        return pendingDownloadStates.has(state);
    }

    function renderDownloadButton(button, card) {
        const itemId = card.dataset.id;
        const name = movieName(card);
        const state = downloadStates.get(itemId);
        const pendingAction = pendingActions.get(itemId);

        button.dataset.itemId = itemId;
        if (!state) {
            setButtonChecking(button, card);
            return;
        }

        if (pendingAction) {
            const deleting = pendingAction.action === 'delete';
            button.dataset.action = deleting ? 'delete' : 'download';
            button.dataset.state = deleting ? 'deleting' : 'requesting';
            button.disabled = true;
            button.title = deleting ? `Waiting to remove ${name}` : `Download requested for ${name}`;
            button.setAttribute('aria-label', button.title);
            button.setAttribute('aria-busy', 'true');
            setButtonIcon(button, deleting ? 'delete' : 'downloading');
            return;
        }

        button.removeAttribute('aria-busy');
        if (isDownloaded(state)) {
            button.dataset.action = 'delete';
            button.dataset.state = 'downloaded';
            button.disabled = false;
            button.title = `Delete downloaded ${name}`;
            button.setAttribute('aria-label', button.title);
            setButtonIcon(button, 'delete');
        } else if (isDownloadPending(state)) {
            button.dataset.action = 'download';
            button.dataset.state = state.toLowerCase();
            button.disabled = true;
            button.title = `Downloading ${name}`;
            button.setAttribute('aria-label', button.title);
            button.setAttribute('aria-busy', 'true');
            setButtonIcon(button, 'downloading');
        } else {
            button.dataset.action = 'download';
            button.dataset.state = absentDownloadStates.has(state) ? 'idle' : state.toLowerCase();
            button.disabled = false;
            button.title = `Download ${name}`;
            button.setAttribute('aria-label', button.title);
            setButtonIcon(button, 'download');
        }
    }

    function renderItemButtons(itemId) {
        document.querySelectorAll(movieCardSelector).forEach(card => {
            if (card.dataset.id !== itemId) return;
            const button = card.querySelector(`.cardScalable > ${downloadButtonSelector}`);
            if (button) renderDownloadButton(button, card);
        });
    }

    function readNativeDownloadStates(itemIds) {
        const downloadsBridge = window.ZadflixDownloads;
        if (typeof downloadsBridge?.getStates !== 'function') {
            return new Map(itemIds.map(itemId => [itemId, 'NONE']));
        }

        return parseDownloadStates(downloadsBridge.getStates(JSON.stringify(itemIds)), itemIds);
    }

    function reconcilePendingActions(now) {
        pendingActions.forEach((pendingAction, itemId) => {
            const state = downloadStates.get(itemId) ?? 'NONE';
            const timeout = pendingAction.action === 'delete'
                ? pendingDeletionTimeoutMs
                : pendingDownloadFeedbackTimeoutMs;
            const timedOut = now - pendingAction.startedAt >= timeout;
            const downloadAccepted = pendingAction.action === 'download'
                && (isDownloadPending(state) || isDownloaded(state));
            const deletionFinished = pendingAction.action === 'delete' && !isDownloaded(state);

            if (timedOut || downloadAccepted || deletionFinished) pendingActions.delete(itemId);
        });

        watchedDownloads.forEach((startedAt, itemId) => {
            if (isDownloaded(downloadStates.get(itemId)) || now - startedAt >= downloadWatchTimeoutMs) {
                watchedDownloads.delete(itemId);
            }
        });
    }

    function scheduleStateRefresh() {
        window.clearTimeout(stateRefreshTimer);
        const visibleItemIds = new Set(
            [...document.querySelectorAll(movieCardSelector)].map(card => card.dataset.id).filter(Boolean),
        );
        const shouldRefresh = [...visibleItemIds].some(itemId =>
            pendingActions.has(itemId)
            || watchedDownloads.has(itemId)
            || isDownloadPending(downloadStates.get(itemId)),
        );
        if (!shouldRefresh) return;

        stateRefreshTimer = window.setTimeout(() => {
            forceStateRefresh = true;
            scheduleMovieCardReconciliation();
        }, stateRefreshIntervalMs);
    }

    function reconcileMovieCards() {
        reconciliationScheduled = false;

        document.querySelectorAll(downloadButtonSelector).forEach(button => {
            if (!button.closest(movieCardSelector)) button.remove();
        });

        document.querySelectorAll(movieCardSelector).forEach(card => {
            const cardScalable = card.querySelector('.cardScalable');
            if (!cardScalable) return;

            const buttons = [...cardScalable.children].filter(child => child.matches?.(downloadButtonSelector));
            let button = buttons.shift();
            buttons.forEach(duplicateButton => duplicateButton.remove());
            if (!button) {
                button = document.createElement('button');
                button.type = 'button';
                button.className = 'cardOverlayButton cardOverlayButton-br paper-icon-button-light zadflix-card-download';
                button.innerHTML = '<span class="material-icons download" aria-hidden="true"></span>';
                cardScalable.appendChild(button);
            }

            if (button.dataset.itemId !== card.dataset.id) {
                setButtonChecking(button, card);
            }
        });

        const cards = [...document.querySelectorAll(movieCardSelector)];
        const itemIds = [...new Set(cards.map(card => card.dataset.id).filter(Boolean))];
        const now = Date.now();
        const missingState = itemIds.some(itemId => !downloadStates.has(itemId));
        if (itemIds.length && (forceStateRefresh || missingState || now - lastStateQueryAt >= stateQueryThrottleMs)) {
            try {
                const currentStates = readNativeDownloadStates(itemIds);
                itemIds.forEach(itemId => downloadStates.set(itemId, currentStates.get(itemId) ?? 'NONE'));
                lastStateQueryAt = now;
                forceStateRefresh = false;
                reconcilePendingActions(now);
            } catch (error) {
                console.error('Zadflix could not read native download state.', error);
                itemIds.forEach(itemId => {
                    if (!downloadStates.has(itemId)) downloadStates.set(itemId, 'NONE');
                });
            }
        }

        cards.forEach(card => {
            const button = card.querySelector(`.cardScalable > ${downloadButtonSelector}`);
            if (button) renderDownloadButton(button, card);
        });
        scheduleStateRefresh();
    }

    function scheduleMovieCardReconciliation() {
        if (reconciliationScheduled) return;
        reconciliationScheduled = true;
        window.requestAnimationFrame(reconcileMovieCards);
    }

    document.addEventListener('click', event => {
        const downloadButton = event.target.closest?.(downloadButtonSelector);
        if (downloadButton) {
            const card = downloadButton.closest(movieCardSelector);
            const itemId = card?.dataset.id;
            if (!itemId) return;

            event.preventDefault();
            event.stopImmediatePropagation();
            if (downloadButton.disabled || pendingActions.has(itemId)) return;

            try {
                const action = downloadButton.dataset.action;
                const accepted = action === 'delete'
                    ? window.ZadflixDownloads?.requestDeletion(itemId)
                    : window.NativeInterface?.downloadFiles(JSON.stringify([{ itemId }]));
                if (accepted !== true) throw new Error(`The Android ${action} request was rejected.`);

                pendingActions.set(itemId, { action, startedAt: Date.now() });
                if (action === 'download') watchedDownloads.set(itemId, Date.now());
                renderItemButtons(itemId);
                forceStateRefresh = true;
                scheduleStateRefresh();
            } catch (error) {
                console.error('Zadflix could not change this download.', error);
                pendingActions.delete(itemId);
                renderDownloadButton(downloadButton, card);
            }
            return;
        }

        const card = event.target.closest?.(videoCardSelector);
        const itemId = card?.dataset.id;
        if (!itemId) return;

        const action = event.target.closest?.('.itemAction[data-action], button[data-action]')?.dataset.action;
        if (action && action !== 'link' && action !== 'play') return;

        if (window.ZadflixOfflinePlayback?.handleItemClick(itemId)) {
            event.preventDefault();
            event.stopImmediatePropagation();
            return;
        }

        if (!card.matches(movieCardSelector) || action === 'play') return;

        const playButton = card.querySelector('.itemAction[data-action="play"]');
        if (!playButton) return;

        event.preventDefault();
        event.stopImmediatePropagation();
        playButton.click();
    }, true);

    const observer = new MutationObserver(mutations => {
        if (mutations.some(mutation => mutation.type === 'attributes')) forceStateRefresh = true;
        scheduleMovieCardReconciliation();
    });
    observer.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ['data-id', 'data-mediatype', 'data-type'],
        childList: true,
        subtree: true,
    });
    window.addEventListener('hashchange', () => {
        forceStateRefresh = true;
        scheduleMovieCardReconciliation();
    });
    window.addEventListener('pageshow', () => {
        forceStateRefresh = true;
        scheduleMovieCardReconciliation();
    });
    window.addEventListener('focus', () => {
        forceStateRefresh = true;
        scheduleMovieCardReconciliation();
    });
    window.addEventListener('zadflix-download-state-changed', event => {
        const itemId = event.detail?.itemId;
        if (itemId) {
            downloadStates.delete(itemId);
            pendingActions.delete(itemId);
            watchedDownloads.delete(itemId);
        }
        forceStateRefresh = true;
        scheduleMovieCardReconciliation();
    });
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState !== 'visible') return;
        forceStateRefresh = true;
        scheduleMovieCardReconciliation();
    });
    scheduleMovieCardReconciliation();
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
