/**
 * Reads retained evidence and writes a reviewable report without starting browser workloads.
 */
const {existsSync, readFileSync, writeFileSync} = require('node:fs');
/**
 * Portable evidence and output paths.
 */
const {resolve} = require('node:path');
/**
 * Uses the same explicitly defined nearest-rank statistics as the matrix.
 */
const {statistics} = require('./browser-lab.cjs');

/**
 * Formats finite measured values while preserving unsupported/missing metrics as unavailable.
 */
function number(value, digits = 1) {
    return Number.isFinite(value) ? value.toFixed(digits) : 'unavailable';
}

/**
 * Formats median/p95 with its actual finite sample count.
 */
function pair(stats) {
    return `${number(stats.median)} / ${number(stats.p95)} (${stats.n})`;
}

/**
 * Generates a finite-coverage lab report; it deliberately makes no field-data or old/new claim.
 */
function report() {
    const matrix = JSON.parse(readFileSync(resolve(process.env.LAB_OUTPUT || '../performance-results/browser-lab.json'), 'utf8'));
    const diagnostics = JSON.parse(readFileSync(resolve(process.env.LAB_DIAGNOSTICS || '../performance-results/browser-lab-diagnostics.json'), 'utf8'));
    const passed = matrix.samples.filter((sample) => sample.status === 'passed');
    const plannedLoads = (matrix.routes || ['/', '/components']).length * matrix.viewports.length * matrix.profiles.length * 2 * matrix.repeats;
    const lines = [
        '# Geshra browser lab',
        '',
        `Recorded ${matrix.recordedAt}; browser ${matrix.environment.browser}, ${matrix.environment.platform}/${matrix.environment.arch}, ${matrix.environment.cpu.trim()}. Headless local lab, current candidate only.`,
        '',
        `Source HEAD: \`${matrix.source.head}\`; source SHA-256: \`${matrix.source.sourceSha256}\`. This fingerprint includes uncommitted maintained sources and harness files at run start.`,
        '',
        `${passed.length}/${matrix.samples.length} matrix fixtures completed; ${matrix.samples.length - passed.length} failed; ${plannedLoads} planned. A completed fixture is not a claim of zero console/network/accessibility findings. ${matrix.repeats} repetitions per condition; nearest-rank p95 with fewer than 20 samples is the maximum, not a reliable population tail estimate.`,
        '',
        '## Explicit matrix',
        '',
        `${(matrix.routes || ['/', '/components']).length} routes (home, component gallery), ${matrix.viewports.length} viewports (1440×900 desktop and 390×844 touch/mobile-sized at DPR2), ${matrix.profiles.length} profiles, cold/warm cache, ${matrix.repeats} repetitions = ${plannedLoads} planned loads. Mobile settings emulate viewport/touch/CPU and retain the desktop browser user agent; this is not a physical phone. Cold means fresh context plus cleared browser cache; warm means an unmeasured same-context route visit before measurement. The server process and packaged static cache remain warm in both.`,
        '',
        '| Profile | HTTP latency ms | Download bytes/s | Upload bytes/s | CPU multiplier |',
        '| --- | ---: | ---: | ---: | ---: |'
    ];
    for (const profile of matrix.profiles)
        lines.push(`| ${profile.name} | ${profile.latencyMs} | ${profile.downloadBytesPerSecond} | ${profile.uploadBytesPerSecond} | ${profile.cpuRate} |`);
    lines.push(
        '',
        '-1 throughput disables the CDP bandwidth limit. Emulated HTTP latency is not labeled WAN RTT or applied WS delay.',
        '',
        '## Page and interaction results',
        '',
        'Values are median / p95 (finite sample count), in milliseconds except CLS. Load LCP is the latest observed entry after route-ready +750ms and before interactions. CLS uses the largest session window (gaps <1s; duration <5s), excluding recent-input shifts. Event Timing interactions come from trusted browser clicks/typing; durations below the requested 16ms observation threshold can be absent. Lab INP is the observed interaction-duration estimate during this short session, not field INP or callback RTT.',
        '',
        '| Route | Viewport | Profile | Cache | TTFB ms | LCP ms | CLS | Lab INP ms | Callback visual proxy ms |',
        '| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: |'
    );
    for (const row of matrix.summary)
        lines.push(
            `| ${row.route} | ${row.viewport} | ${row.profile} | ${row.cache} | ${pair(row.ttfbMs)} | ${pair(row.lcpMs)} | ${number(row.cls.median, 4)} / ${number(row.cls.p95, 4)} | ${pair(row.labInpMs)} | ${pair(row.callbackVisualMs)} |`
        );
    lines.push(
        '',
        `Callback visual proxy spans the trusted click handler to a server-driven DOM mutation plus two animation frames. It approximates presentation, not measured display scanout; it is kept separate from INP. Three callbacks per document yield up to ${matrix.repeats * 3} callback samples per condition. Real keyboard input adds five characters on gallery visits.`,
        '',
        '## Rendering and network evidence',
        '',
        '| Profile (all routes/viewports/cache states pooled) | FCP ms | DCL ms | Load ms | JS script ms | Style ms | Layout ms | Observed blocking ms |',
        '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |'
    );
    for (const profile of matrix.profiles) {
        const samples = passed.filter((sample) => sample.profile === profile.name);
        lines.push(
            `| ${profile.name} | ${pair(statistics(samples.map((sample) => sample.load.fcpMs)))} | ${pair(statistics(samples.map((sample) => sample.load.navigation.domContentLoadedMs)))} | ${pair(statistics(samples.map((sample) => sample.load.navigation.loadMs)))} | ${pair(statistics(samples.map((sample) => sample.loadCpu.ScriptDuration * 1000)))} | ${pair(statistics(samples.map((sample) => sample.loadCpu.RecalcStyleDuration * 1000)))} | ${pair(statistics(samples.map((sample) => sample.loadCpu.LayoutDuration * 1000)))} | ${pair(statistics(samples.map((sample) => sample.metrics.observedBlockingMs)))} |`
        );
    }
    const requests = passed.flatMap((sample) => sample.network.requests);
    const failedRequests = requests.filter((request) => request.error || request.status >= 400);
    const errors = passed.flatMap((sample) => sample.errors);
    const supported = passed[0]?.metrics.raw.supported || [];
    lines.push(
        '',
        'Pooling is descriptive only; compare individual conditions above for controlled comparisons. CDP duration counters are deltas over measured navigation through the settle interval. Long-task excess over 50 ms is an observed lab blocking indicator, not Lighthouse TBT (no TTI algorithm). Raw data includes FP, DNS/connect/TLS, request TTFB, application-ready time, all resource encoded/decoded/transfer sizes, HTTP waterfall/status/cache/encoding, WS handshake times and frame bytes. Loopback DNS/TLS zeros do not predict Internet DNS/TLS costs.',
        '',
        `HTTP requests across matrix: ${requests.length}; cache-served flags: ${requests.filter((request) => request.cache).length}; HTTP content-encoding gzip responses: ${requests.filter((request) => request.encoding === 'gzip').length}; error/status>=400 requests: ${failedRequests.length}; console/page errors: ${errors.length}; horizontal-overflow samples: ${passed.filter((sample) => sample.layout.horizontalOverflow).length}. Console scope: ${matrix.consoleCaptureIncludesWarmPrime === false ? 'measured navigation only' : 'this retained run also includes warm-prime console messages; do not compare its console count directly with measured-request failures. The current harness clears priming errors before measurement'}.`,
        '',
        'Request failures/statuses:',
        '',
        '```json',
        JSON.stringify(
            Array.from(new Set(failedRequests.map((request) => JSON.stringify({url: request.url, status: request.status, error: request.error})))).map((value) =>
                JSON.parse(value)
            ),
            null,
            2
        ),
        '```',
        '',
        'Supported PerformanceObserver entry types:',
        '',
        '`' + supported.join(', ') + '`',
        '',
        '## Separate diagnostic results',
        ''
    );
    for (const entry of diagnostics.cases) {
        lines.push(`### ${entry.name}: ${entry.status}`, '');
        if (entry.status !== 'passed') {
            lines.push('```', entry.error, '```', '');
            continue;
        }
        if (entry.name === 'websocketThrottle') {
            lines.push('| Experiment | DOM mutation ms median/p95 | Visual proxy ms median/p95 |', '| --- | ---: | ---: |');
            for (const mode of entry.result) lines.push(`| ${mode.mode} | ${pair(mode.mutationMs)} | ${pair(mode.visualProxyMs)} |`);
            lines.push(
                '',
                'The 400 ms HTTP experiment isolates latency with CPU 1 and unlimited bandwidth. Its native WS callback results show whether that installed CDP command affects WS messages. The separate real-server relay injects 75 ms per direction, preserving message order for this sequential workload; it is not a TCP loss, bandwidth, fragmentation, or congestion model.',
                ''
            );
        } else if (entry.name === 'memory') {
            const snapshots = entry.result.snapshots;
            lines.push(
                `Forced-GC heap used: ${snapshots[0].heap.usedSize} → ${snapshots.at(-1).heap.usedSize} bytes across 12 same-document home/gallery cycles. DOM nodes: ${snapshots[0].dom.nodes} → ${snapshots.at(-1).dom.nodes}; JS listeners: ${snapshots[0].dom.jsEventListeners} → ${snapshots.at(-1).dom.jsEventListeners}. Growth may include warmup/JIT/harness caches; this is not proof of either a leak or its absence.`,
                '',
                '```json',
                JSON.stringify(entry.result.lifecycle, null, 2),
                '```',
                ''
            );
        } else if (entry.name === 'rendering') {
            lines.push(
                `Ten trusted wheel actions: ${entry.result.scrolling.rows} live rows, final scrollTop ${entry.result.scrolling.scrollTop}, frame interval ms ${pair(entry.result.scrolling.frameIntervals)}. The trace/screenshot run includes instrumentation and is not compared with matrix timings.`,
                '',
                '```json',
                JSON.stringify({audit: entry.result.audit, traceTotalsMs: entry.result.traceTotalsMs}, null, 2),
                '```',
                '',
                'The control-name check and native accessibility tree expose potential issues; they do not test contrast, screen readers, focus order completeness, or certify WCAG conformance. Trace durations can nest and must not be summed as exclusive CPU time.',
                ''
            );
        } else lines.push('```json', JSON.stringify(entry.result, null, 2), '```', '');
    }
    const firefoxPath = resolve(process.env.LAB_FIREFOX || '../performance-results/browser-lab-firefox.json');
    if (existsSync(firefoxPath)) {
        const firefox = JSON.parse(readFileSync(firefoxPath, 'utf8'));
        const completed = firefox.samples.filter((sample) => sample.status === 'passed');
        lines.push(
            '## Separate Firefox subset',
            '',
            `Firefox ${firefox.browser}: ${completed.length}/${firefox.samples.length} fixtures completed. ${firefox.conditions}. No CDP network/CPU/trace measurements apply to Firefox. Two repeats per route/viewport; p95 is the maximum.`,
            '',
            '| Route | Viewport | LCP ms median/p95 | CLS | Lab INP | Long tasks | Callback visual proxy ms |',
            '| --- | --- | ---: | --- | --- | --- | ---: |'
        );
        for (const route of ['/', '/components'])
            for (const width of [1440, 390]) {
                const samples = completed.filter((sample) => sample.route === route && sample.viewport.width === width);
                const support = samples[0]?.metrics.raw.supported || [];
                lines.push(
                    `| ${route} | ${width} | ${pair(statistics(samples.map((sample) => sample.load.lcpMs)))} | ${support.includes('layout-shift') ? pair(statistics(samples.map((sample) => sample.metrics.cls))) : 'unsupported'} | ${pair(statistics(samples.map((sample) => sample.metrics.labInpMs)))} | ${support.includes('longtask') ? 'supported' : 'unsupported'} | ${pair(statistics(samples.map((sample) => sample.callback.visualProxyMs)))} |`
                );
            }
        lines.push(
            '',
            'Unavailable INP means no qualifying Event Timing entries were observed; it is not zero. Unsupported CLS/Long Tasks are null in the raw data. Firefox subset page errors: ' +
                completed.flatMap((sample) => sample.errors).length +
                '. Native HTTP/WS profiling remains Chrome-only.',
            ''
        );
    } else lines.push('## Separate Firefox subset', '', 'Not run: no Firefox evidence file was supplied.', '');
    lines.push(
        '## Scope and remaining tests',
        '',
        '- Current-candidate lab characterization only; previous payload artifacts remain preserved, but no comparable throttled baseline was collected.',
        '- No physical mobile device, Safari/WebKit, real WAN/TLS/DNS, OS sleep, real background visibility scheduling, multi-user capacity, packet loss, or network bandwidth shaping of WS messages.',
        '- Actual CDP offline/online with socket closure and queued callback replay, reconnect, frozen/active renderer resume, explicit retain/restore/dispose, and 12 GC-observed route cycles are tested separately.',
        '- Existing Chromium/Firefox protocol/component/auth suites cover native controls, login/logout/cookies, keyboard Escape, virtual-list bounds, missing capability state and late-state downgrade. Their run status is recorded in the accompanying documentation, not inferred from these matrix cases.',
        '- Simulated server-message delay is not server-side Netty backpressure. JVM backpressure regression coverage remains separate; sustained browser receive-bandwidth exhaustion is not tested.',
        '- LCP/CLS capture is bounded to the scripted session; later application behavior, arbitrary customer scripts, scroll-driven content and field user distributions can differ.',
        '- Local diagnostics include mobile screenshot, Chrome trace and sanitized HAR under build/reports/browser-lab. The JSON report is retained alongside matrix results; raw matrix samples support independent inspection.',
        '',
        'Metric semantics: [INP](https://web.dev/articles/inp), [CLS](https://web.dev/articles/cls), [CDP Network](https://chromedevtools.github.io/devtools-protocol/tot/Network/), [Playwright WebSocket relay](https://playwright.dev/docs/api/class-websocketroute).',
        ''
    );
    writeFileSync(resolve(process.env.LAB_REPORT || '../performance-results/BROWSER-LAB.md'), lines.join('\n'));
}

report();
