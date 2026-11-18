/**
 * Real-browser diagnostics run separately from the timing matrix to avoid trace/GC interference.
 */
const {chromium} = require('playwright');
/**
 * Diagnostic artifact storage.
 */
const {mkdirSync, readFileSync, writeFileSync} = require('node:fs');
/**
 * Resolves output locations independently of the repository root.
 */
const {resolve} = require('node:path');
/**
 * Reuses the matrix's actual route readiness and trusted callback fixture.
 */
const {ready, callbackSample, statistics, provenance} = require('./browser-lab.cjs');
/**
 * Uses identical HTTP emulation settings for the isolated WebSocket throttle experiment.
 */
const {applyProfile} = require('./lab-network.cjs');

/**
 * Navigates an existing UI over its real socket using the documented legacy route packet.
 * This avoids full document reloads, making repeated-route retention visible to GC snapshots.
 */
async function navigateRoute(page, route) {
    await page.evaluate((path) => {
        const text = new TextEncoder().encode(path);
        const packet = new Uint8Array(3 + text.length);
        new DataView(packet.buffer).setUint16(1, text.length);
        packet.set(text, 3);
        window.domSocket.send(packet);
    }, route);
    await ready(page, route);
}

/**
 * Isolates HTTP throttling from WS traffic, then adds a known 75ms message delay in each
 * direction through Playwright's real-server WS relay. The relay is not TCP bandwidth shaping.
 */
async function websocketExperiment(browser, base) {
    const observations = [];
    for (const mode of ['native-local', 'native-http400ms', 'relay-75ms-each-way']) {
        const context = await browser.newContext();
        const page = await context.newPage();
        const cdp = await context.newCDPSession(page);
        const timers = new Set();
        let closed = false;
        try {
            if (mode.startsWith('relay'))
                await page.routeWebSocket('**/ws', (route) => {
                    const server = route.connectToServer();
                    for (const [source, destination] of [
                        [route, server],
                        [server, route]
                    ])
                        source.onMessage((message) => {
                            const timer = setTimeout(() => {
                                timers.delete(timer);
                                if (!closed) destination.send(message);
                            }, 75);
                            timers.add(timer);
                        });
                });
            await cdp.send('Network.enable');
            await applyProfile(cdp, {latencyMs: mode === 'native-http400ms' ? 400 : 0, downloadBytesPerSecond: -1, uploadBytesPerSecond: -1, cpuRate: 1});
            await page.goto(base + '/');
            await ready(page, '/');
            await callbackSample(page, '/');
            const samples = [];
            for (let index = 0; index < 10; index++) samples.push(await callbackSample(page, '/'));
            observations.push({
                mode,
                samples,
                mutationMs: statistics(samples.map((sample) => sample.mutationMs)),
                visualProxyMs: statistics(samples.map((sample) => sample.visualProxyMs))
            });
        } finally {
            closed = true;
            for (const timer of timers) clearTimeout(timer);
            await context.close();
        }
    }
    return observations;
}

/**
 * Exercises native reconnect, offline queue replay, and frozen-renderer resume with actual
 * sockets and server callbacks. Visibility support is recorded rather than inferred in headless.
 */
async function recoveryExperiment(browser, base) {
    const context = await browser.newContext();
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    try {
        await page.goto(base + '/');
        await ready(page, '/');
        await page.evaluate(() => {
            window.previousLabSocket = window.domSocket;
            window.domSocket.close();
        });
        await page.waitForFunction(() => window.domSocket !== window.previousLabSocket && window.domSocket.readyState === 1);
        const reconnect = await callbackSample(page, '/');
        await context.setOffline(true);
        await page.locator('h1').evaluate((element) => {
            element.textContent = 'Awaiting offline replay';
        });
        await page.evaluate(() => {
            window.previousLabSocket = window.domSocket;
            window.domSocket.close();
        });
        await page.getByRole('button', {name: 'Click me', exact: true}).click();
        await page.waitForTimeout(800);
        await context.setOffline(false);
        await page.waitForFunction(() => window.domSocket !== window.previousLabSocket && window.domSocket.readyState === 1);
        await page.getByRole('heading', {name: 'The Java listener handled your click', exact: true}).waitFor();
        await page.waitForTimeout(200);
        const online = await callbackSample(page, '/');
        await cdp.send('Page.setWebLifecycleState', {state: 'frozen'});
        await new Promise((resolve) => setTimeout(resolve, 250));
        await cdp.send('Page.setWebLifecycleState', {state: 'active'});
        const resume = await callbackSample(page, '/');
        return {
            reconnect,
            offlineQueuedCallbackReplayed: true,
            online,
            resume,
            visibilityState: await page.evaluate(() => document.visibilityState),
            backgroundVisibility: 'not simulated; renderer frozen/active was tested, OS sleep and real background scheduling remain untested'
        };
    } finally {
        await context.close();
    }
}

/**
 * Measures same-document UI churn after forced GC, and verifies explicit retained-node identity
 * and disposed listener removal. Browser memory counters are diagnostics, not process RSS.
 */
async function memoryExperiment(browser, base) {
    const context = await browser.newContext();
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    const snapshots = [];
    try {
        await page.goto(base + '/components');
        await ready(page, '/components');
        for (let cycle = 0; cycle <= 12; cycle++) {
            if (cycle) {
                await navigateRoute(page, '/');
                await navigateRoute(page, '/components');
            }
            await page.waitForTimeout(50);
            await cdp.send('HeapProfiler.collectGarbage');
            snapshots.push({cycle, heap: await cdp.send('Runtime.getHeapUsage'), dom: await cdp.send('Memory.getDOMCounters'), liveNodes: await page.locator('*').count()});
        }
        const input = page.getByLabel('Retained input');
        await input.fill('retained lab value');
        await input.evaluate((element) => {
            window.labRetained = element;
            element.setSelectionRange(2, 5);
        });
        await page.getByRole('button', {name: 'Detach input', exact: true}).click();
        await input.waitFor({state: 'detached'});
        const detachedConnected = await page.evaluate(() => window.labRetained.isConnected);
        await page.getByRole('button', {name: 'Restore input', exact: true}).click();
        await input.waitFor();
        const restoredIdentity = await input.evaluate((element) => element === window.labRetained);
        const restoredValue = await input.inputValue();
        // Observe the existing socket through its native send method without retaining payloads.
        await page.evaluate(() => {
            window.labSendCount = 0;
            const socket = window.domSocket;
            const original = socket.send.bind(socket);
            socket.send = (packet) => {
                window.labSendCount++;
                original(packet);
            };
        });
        await page.getByRole('button', {name: 'Dispose input', exact: true}).click();
        await input.waitFor({state: 'detached'});
        const staleSends = await page.evaluate(() => {
            const before = window.labSendCount;
            window.labRetained.dispatchEvent(new Event('input', {bubbles: true}));
            const sent = window.labSendCount - before;
            window.labWeak = new WeakRef(window.labRetained);
            delete window.labRetained;
            return sent;
        });
        await cdp.send('HeapProfiler.collectGarbage');
        const collected = await page.evaluate(() => window.labWeak.deref() === undefined);
        if (detachedConnected || !restoredIdentity || restoredValue !== 'retained lab value' || staleSends !== 0) throw new Error('Retain/dispose lifecycle regression');
        return {
            snapshots,
            lifecycle: {detachedConnected, restoredIdentity, restoredValue, staleSends, disposedWeakReferenceCollected: collected},
            limitations: 'Twelve cycles and forced GC do not prove absence of leaks; DOM counters include renderer-owned nodes.'
        };
    } finally {
        await context.close();
    }
}

/**
 * Uses trusted wheel events against the million-item list, retaining raw frame intervals and
 * bounded live-row counts. Screenshot/trace capture is separate from matrix timing samples.
 */
async function renderingExperiment(browser, base, directory) {
    const context = await browser.newContext({
        viewport: {width: 390, height: 844},
        isMobile: true,
        hasTouch: true,
        deviceScaleFactor: 2,
        recordHar: {path: resolve(directory, 'gallery.har'), content: 'omit'}
    });
    const page = await context.newPage();
    const cdp = await context.newCDPSession(page);
    try {
        await cdp.send('Tracing.start', {categories: 'devtools.timeline,blink.user_timing', transferMode: 'ReturnAsStream'});
        await page.goto(base + '/components');
        await ready(page, '/components');
        await page.screenshot({path: resolve(directory, 'gallery-mobile.png'), fullPage: true});
        await page.evaluate(() => {
            window.labFrames = [];
            window.labFrameRunning = true;
            let previous = performance.now();
            const frame = (time) => {
                window.labFrames.push(time - previous);
                previous = time;
                if (window.labFrameRunning) requestAnimationFrame(frame);
            };
            requestAnimationFrame(frame);
        });
        const list = page.getByLabel('Virtual dataset');
        await list.scrollIntoViewIfNeeded();
        await list.hover();
        for (let index = 0; index < 10; index++) {
            await page.mouse.wheel(0, 500);
            await page.waitForTimeout(75);
        }
        await page.waitForTimeout(250);
        const scrolling = await page.evaluate(() => {
            window.labFrameRunning = false;
            return {
                frameIntervalsMs: window.labFrames,
                rows: document.querySelectorAll('.geshra-virtual-row').length,
                first: document.querySelector('.geshra-virtual-row')?.getAttribute('aria-posinset'),
                scrollTop: document.querySelector('[aria-label="Virtual dataset"]').scrollTop
            };
        });
        if (scrolling.rows > 20 || scrolling.scrollTop === 0) throw new Error('Virtual scrolling did not remain bounded');
        const accessibility = await cdp.send('Accessibility.getFullAXTree');
        const audit = await page.evaluate(() => ({
            title: document.title,
            language: document.documentElement.lang,
            missingControlNames: Array.from(document.querySelectorAll('input,select,textarea,button'))
                .filter(
                    (element) =>
                        !element.getAttribute('aria-label') &&
                        !element.getAttribute('aria-labelledby') &&
                        !element.labels?.length &&
                        !(element.tagName === 'BUTTON' && element.textContent.trim())
                )
                .map((element) => ({tag: element.tagName, type: element.type})),
            overflow: document.documentElement.scrollWidth > innerWidth + 1
        }));
        await page.keyboard.press('Tab');
        audit.keyboardFocus = await page.evaluate(() => ({tag: document.activeElement.tagName, name: document.activeElement.getAttribute('aria-label')}));
        const complete = new Promise((resolve) => cdp.once('Tracing.tracingComplete', resolve));
        await cdp.send('Tracing.end');
        const {stream} = await complete;
        let trace = '';
        while (true) {
            const chunk = await cdp.send('IO.read', {handle: stream});
            trace += chunk.base64Encoded ? Buffer.from(chunk.data, 'base64').toString() : chunk.data;
            if (chunk.eof) break;
        }
        await cdp.send('IO.close', {handle: stream});
        writeFileSync(resolve(directory, 'gallery-trace.json'), trace);
        const events = JSON.parse(trace).traceEvents;
        const traceTotals = {};
        for (const event of events)
            if (event.ph === 'X' && ['Paint', 'Layout', 'UpdateLayoutTree', 'EvaluateScript', 'FunctionCall'].includes(event.name))
                traceTotals[event.name] = (traceTotals[event.name] || 0) + (event.dur || 0) / 1000;
        return {
            scrolling: {...scrolling, frameIntervals: statistics(scrolling.frameIntervalsMs)},
            audit,
            accessibility: {
                nodes: accessibility.nodes.length,
                ignored: accessibility.nodes.filter((node) => node.ignored).length,
                namedRoles: accessibility.nodes.filter((node) => !node.ignored && node.name?.value).map((node) => ({role: node.role.value, name: node.name.value}))
            },
            traceTotalsMs: traceTotals,
            note: 'Trace category durations can nest; never sum them as exclusive CPU time. Name audit is a heuristic, not WCAG certification.'
        };
    } finally {
        await context.close();
        // HARs contain live session cookies unless request/response headers and cookies are scrubbed.
        const harPath = resolve(directory, 'gallery.har');
        try {
            const har = JSON.parse(readFileSync(harPath, 'utf8'));
            for (const entry of har.log.entries)
                for (const side of [entry.request, entry.response]) {
                    side.cookies = [];
                    side.headers = side.headers.filter((header) => !/cookie|authorization|csrf/i.test(header.name));
                }
            writeFileSync(harPath, JSON.stringify(har, null, 2) + '\n');
        } catch (error) {
            throw new Error('Unable to sanitize diagnostic HAR', {cause: error});
        }
    }
}

/**
 * Runs independent diagnostics sequentially and retains failures without calling them passes.
 */
async function runDiagnostics() {
    const directory = resolve(process.env.LAB_ARTIFACTS || '../build/reports/browser-lab');
    mkdirSync(directory, {recursive: true});
    const result = {recordedAt: new Date().toISOString(), browser: null, source: provenance(), cases: []};
    const browser = await chromium.launch({headless: true, executablePath: process.env.BROWSER_EXECUTABLE || undefined});
    try {
        result.browser = browser.version();
        for (const [name, action] of [
            ['websocketThrottle', websocketExperiment],
            ['recovery', recoveryExperiment],
            ['memory', memoryExperiment],
            ['rendering', renderingExperiment]
        ]) {
            try {
                result.cases.push({name, status: 'passed', result: await action(browser, process.env.BASE_URL || 'http://localhost:4040', directory)});
            } catch (error) {
                result.cases.push({name, status: 'failed', error: error.stack});
            }
            writeFileSync(resolve(directory, 'diagnostics.json'), JSON.stringify(result, null, 2) + '\n');
            console.log(JSON.stringify(result.cases.at(-1)));
        }
    } finally {
        await browser.close();
    }
    if (result.cases.some((entry) => entry.status === 'failed')) process.exitCode = 1;
}

runDiagnostics().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
