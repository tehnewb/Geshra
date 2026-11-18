/**
 * Retained evidence only: this generator never runs or changes an application.
 */
const {readFileSync, writeFileSync} = require('node:fs');
/**
 * Resolves evidence independently of the caller's working directory.
 */
const {resolve} = require('node:path');
/**
 * Uses the ordinary sample median (middle-pair average for even counts) and nearest-rank p95.
 * Missing metrics remain unavailable, and p95 with four samples is the observed maximum.
 */
function statistics(values) {
    const finite = values.filter((number) => Number.isFinite(number)).sort((left, right) => left - right);
    const middle = Math.floor(finite.length / 2);
    return {
        n: finite.length,
        median: finite.length ? (finite.length % 2 ? finite[middle] : (finite[middle - 1] + finite[middle]) / 2) : null,
        p95: finite.length ? finite[Math.ceil(finite.length * 0.95) - 1] : null
    };
}

/**
 * Formats unavailable observations without turning missing metrics into zero.
 */
function value(number, digits = 1) {
    return Number.isFinite(number) ? number.toFixed(digits) : 'unavailable';
}

/**
 * Formats median/p95 with actual finite observation count.
 */
function summary(values, digits = 1) {
    const stats = statistics(values);
    return `${value(stats.median, digits)} / ${value(stats.p95, digits)} (${stats.n})`;
}

/**
 * Sums Resource Timing encoded or decoded body bytes for one asset class, including HTML.
 * HTTP transferSize and raw relay bytes are separate fields because their overhead differs.
 */
function assetBytes(sample, type, field) {
    if (type === 'html') return sample.load.navigation[field === 'encodedBytes' ? 'encodedBytes' : 'decodedBytes'];
    return sample.load.resources
        .filter((resource) => new URL(resource.name).pathname.endsWith(type === 'js' ? '.js' : '.css'))
        .reduce((total, resource) => total + resource[field], 0);
}

/**
 * Produces workload-specific tables and explicit limitations from the completed actual run.
 */
function report() {
    const root = resolve(__dirname, '../../performance-results');
    const result = JSON.parse(readFileSync(resolve(process.env.COMPARISON_OUTPUT || resolve(root, 'comparison.json')), 'utf8'));
    const inventory = JSON.parse(readFileSync(resolve(root, 'comparison-inventory.json'), 'utf8'));
    const diagnostics = JSON.parse(readFileSync(resolve(root, 'comparison-diagnostics.json'), 'utf8'));
    const passed = result.samples.filter((sample) => sample.status === 'passed');
    for (const sample of passed) {
        const expectedProfile = result.profiles.find((profile) => profile.name === sample.profile);
        if (sample.appliedDelayMs !== expectedProfile.delayEachWayMs) throw new Error('Evidence has unverified relay conditions');
        for (const operation of sample.operations)
            if (operation.operation === 'Select 500' && operation.correctness.rows !== 1000) throw new Error('Evidence selection changed the required row count');
    }
    const planned = result.implementations.length * result.profiles.length * 2 * result.repeats;
    const lines = [
        '# Matched application comparison',
        '',
        `Recorded ${result.recordedAt}; Chrome ${result.environment.browser}, ${result.environment.platform}/${result.environment.arch}, ${result.environment.cpu.trim()}, Node ${result.environment.node}. Desktop viewport ${result.viewport.width}×${result.viewport.height}. Current Geshra candidate; separate from earlier gallery/browser-lab runs.`,
        '',
        `${passed.length}/${result.samples.length} fixtures completed; ${result.samples.length - passed.length} failed; ${planned} planned. ${result.repeats} repetitions per app/profile/cache condition. Values below are median / nearest-rank p95 (sample count). With four repeats, p95 is the largest observed value, not a reliable population percentile. No statistical universal-speed ranking is claimed.`,
        '',
        `Fixture source SHA-256: \`${result.sourceSha256}\`. Effective packaged versions, full dependency lists, production metadata and SHA-256 artifact digests are in comparison-inventory.json. Geshra uses its existing Spring Boot 3.4.3 runtime; Vaadin 25.3.5 uses Spring Boot 4.1.2. Both run Java 21. Client versions: React/React DOM 19.3.1, esbuild 0.28.2.`,
        '',
        '## What is matched',
        '',
        'Native unthemed controls and native two-column table; identical shared stylesheet, visible text, data and operation semantics. The initial app shells contain no visible SSR/SEO application content. Seven buttons, one text input, three status paragraphs, 1,000 actual rows when created; no virtualization. Each button performs a real authoritative server operation before displaying the result. Geshra/Flow own native UI components on the server. React/plain DOM consume deltas from the same real session-owned Node service. React uses production createRoot, stable row keys, standard memoized native rows/table and immutable state updates; no dev server, HMR, custom reconciler or React Compiler.',
        '',
        'The workload is server-dependent. React can update local-only state without a server round trip; this test does not rank local-state rendering or claim that network work is necessary for every React UI. Geshra/Flow eagerly synchronize typed input; API clients submit the value on Echo. Typing bytes are preserved separately, and whole-workload totals include them. Vaadin uses @NoTheme, full-duplex WS and a forced app-specific optimized production bundle; its general precompiled component bundle is not used. These libraries still offer different features, security/session machinery and backend architectures.',
        '',
        'Initial screenshots are checked separately along with common CSS and layout. Diagnostic screenshots and GC interfere with timings and are excluded from the timing matrix.',
        '',
        '## Controlled conditions',
        '',
        'Each profile has one excluded complete workload warmup per implementation. Each repetition rotates implementation order; with four implementations/four repetitions, every library occupies each order position once per profile/cache condition. All runs are sequential. Cold browser cache uses a fresh context and cleared cache, not a cold server; warm cache primes the same context without measured operations. JIT/server resource caches remain warm. The delayed profile uses the same raw TCP relay for every app, applying 75 ms per direction per chunk to both HTTP and WS, plus 4× browser CPU slowdown. Local has zero added delay and 1× CPU. This is latency emulation, not bandwidth/loss shaping, real Internet RTT or measured physical mobile hardware.',
        '',
        '## Initial render and payload',
        '',
        'Ready means the complete matched native content plus two animation frames; first successful real server actions validate usability separately. LCP is the latest observed preinteraction entry after a 750 ms settling interval. Initial stream bytes include delivered HTTP headers and WS bootstrap traffic; exclude TCP/IP/TLS and close traffic. JavaScript/CSS/HTML encoded body sizes below use Resource Timing and are separate from stream-byte totals.',
        '',
        '| Profile | Cache | App | Ready ms | LCP ms | Initial server→browser stream bytes | Initial HTTP requests |',
        '| --- | --- | --- | ---: | ---: | ---: | ---: |'
    ];
    for (const profile of result.profiles)
        for (const cache of ['cold', 'warm'])
            for (const implementation of result.implementations) {
                const samples = passed.filter((sample) => sample.implementation === implementation.name && sample.profile === profile.name && sample.cache === cache);
                lines.push(
                    `| ${profile.name} | ${cache} | ${implementation.name} | ${summary(samples.map((sample) => sample.readyMs))} | ${summary(samples.map((sample) => sample.load.lcpMs))} | ${summary(
                        samples.map((sample) => sample.loadBytes.serverToClient),
                        0
                    )} | ${summary(
                        samples.map((sample) => sample.loadNetwork.requests.length),
                        0
                    )} |`
                );
            }
    lines.push('', 'Cold local body bytes (median encoded / decoded; bodies, excluding protocol headers):', '', '| App | HTML | JS | CSS |', '| --- | ---: | ---: | ---: |');
    for (const implementation of result.implementations) {
        const samples = passed.filter((sample) => sample.implementation === implementation.name && sample.profile === 'local' && sample.cache === 'cold');
        lines.push(
            `| ${implementation.name} | ${['html', 'js', 'css'].map((type) => value(statistics(samples.map((sample) => assetBytes(sample, type, 'encodedBytes'))).median, 0) + ' / ' + value(statistics(samples.map((sample) => assetBytes(sample, type, 'decodedBytes'))).median, 0)).join(' | ')} |`
        );
    }
    lines.push(
        '',
        'Warm-cache results must be considered too: cache policies differ and are preserved, not forcibly equalized. A framework with small cold assets can still transfer more bytes on warm navigation if it does not reuse the HTTP cache. Raw HTTP status/encoding/cache/decoded bytes and every resource are retained.',
        '',
        'A cold-start footprint advantage must not be generalized to every transfer window. Compare warm navigation separately, and compare both directions of whole-workload traffic. In this fixture the API clients compress row data rather than transmitting native UI creation commands; bulk downstream bytes and client event bytes therefore show different tradeoffs. The tables retain these costs separately.',
        '',
        '## Authoritative operations',
        '',
        'Each measured document performs 3 increments, Echo(Ada), create 1,000 rows, update every tenth label (identifiers 1,11,…), select 500, remove 500 and clear. Every final revision and all row contents are verified. These tables pool cold/warm documents within the named profile; they are descriptive, not isolated local-render microbenchmarks. Mutation time is trusted click handler → server-produced revision; visual proxy adds two animation frames and does not prove display scanout. Both remain distinct from INP.',
        '',
        '| Profile | App | Increment DOM ms | Create1000 DOM ms | Update tenth DOM ms | Select DOM ms | Remove DOM ms | Clear DOM ms |',
        '| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |'
    );
    for (const profile of result.profiles)
        for (const implementation of result.implementations) {
            const samples = passed.filter((sample) => sample.implementation === implementation.name && sample.profile === profile.name);
            const operations = samples.flatMap((sample) => sample.operations);
            lines.push(
                `| ${profile.name} | ${implementation.name} | ${['Increment', 'Create 1000', 'Update tenth', 'Select 500', 'Remove 500', 'Clear'].map((name) => summary(operations.filter((operation) => operation.operation === name).map((operation) => operation.mutationMs))).join(' | ')} |`
            );
        }
    lines.push(
        '',
        '| Profile | App | Echo DOM ms | Create visual proxy ms | Workload browser→server stream bytes | Workload server→browser stream bytes |',
        '| --- | --- | ---: | ---: | ---: | ---: |'
    );
    for (const profile of result.profiles)
        for (const implementation of result.implementations) {
            const samples = passed.filter((sample) => sample.implementation === implementation.name && sample.profile === profile.name);
            lines.push(
                `| ${profile.name} | ${implementation.name} | ${summary(samples.flatMap((sample) => sample.operations.filter((operation) => operation.operation === 'Echo').map((operation) => operation.mutationMs)))} | ${summary(samples.flatMap((sample) => sample.operations.filter((operation) => operation.operation === 'Create 1000').map((operation) => operation.visualProxyMs)))} | ${summary(
                    samples.map((sample) => sample.operationsBytes.clientToServer),
                    0
                )} | ${summary(
                    samples.map((sample) => sample.operationsBytes.serverToClient),
                    0
                )} |`
            );
        }
    lines.push(
        '',
        'Whole-workload byte windows include native typing and a 500 ms post-operation tail for protocol acknowledgments. Per-operation byte windows in raw JSON end at the visual proxy; delayed protocol acknowledgments can fall into a later window, so they are not exact lifetime per-action billing. Heartbeat/connection-close traffic outside the window is excluded. HTTP and WS payload formats differ; the common relay counts real application-stream bytes rather than comparing JSON body bytes against framed WS bytes.',
        '',
        'Compression is preserved rather than disabled: the API clients receive gzip-compressed JSON. The separate stock-server handshake diagnostic recorded these negotiated WebSocket extensions: ' +
            diagnostics.samples
                .map((sample) => sample.implementation + '=' + (sample.websocketExtensions?.length ? sample.websocketExtensions.join('; ') : 'not used'))
                .join(', ') +
            '. Thus bulk-transfer results reflect both UI representation and actual compression settings, not a library-independent codec ranking.',
        '',
        '## Browser work and responsiveness',
        '',
        '| Profile | App | Initial Script ms | Create Script ms | Create Layout ms | CLS | Observed lab INP ms |',
        '| --- | --- | ---: | ---: | ---: | ---: | ---: |'
    );
    for (const profile of result.profiles)
        for (const implementation of result.implementations) {
            const samples = passed.filter((sample) => sample.implementation === implementation.name && sample.profile === profile.name);
            const creates = samples.flatMap((sample) => sample.operations.filter((operation) => operation.operation === 'Create 1000'));
            lines.push(
                `| ${profile.name} | ${implementation.name} | ${summary(samples.map((sample) => sample.loadCpu.ScriptDuration * 1000))} | ${summary(creates.map((operation) => operation.cpu.ScriptDuration * 1000))} | ${summary(creates.map((operation) => operation.cpu.LayoutDuration * 1000))} | ${summary(
                    samples.map((sample) => sample.metrics.cls),
                    4
                )} | ${summary(samples.map((sample) => sample.metrics.labInpMs))} |`
            );
        }
    lines.push(
        '',
        'CDP duration deltas are browser counters, not server CPU or end-to-end CPU cost. CLS uses largest session windows and excludes recent-input shifts; INP is a short-session observed Event Timing estimate from genuine clicks/typing, not field p75 or asynchronous server latency. Unsupported/missing entries remain unavailable. Raw observations retain long tasks, timings, support flags, resource waterfalls, WS frames and CPU counters.',
        '',
        '## Retained browser state and visual checks',
        '',
        '| App | Initial DOM nodes → after 6 cycles | JS listeners | Used JS heap bytes | Native bounds/font/title matched |',
        '| --- | ---: | ---: | ---: | --- |'
    );
    for (const sample of diagnostics.samples) {
        if (sample.status !== 'passed') {
            lines.push(`| ${sample.implementation} | failed | failed | failed | ${sample.error} |`);
            continue;
        }
        const first = sample.snapshots[0];
        const last = sample.snapshots.at(-1);
        lines.push(
            `| ${sample.implementation} | ${first.dom.nodes} → ${last.dom.nodes} | ${first.dom.jsEventListeners} → ${last.dom.jsEventListeners} | ${first.heap.usedSize} → ${last.heap.usedSize} | ${JSON.stringify(sample.layout.mainBounds)}; ${sample.layout.font}; ${sample.layout.title} |`
        );
    }
    const errors = passed.flatMap((sample) => sample.errors);
    const requests = passed.flatMap((sample) => sample.network.requests);
    const failedRequests = requests.filter((request) => request.error || request.status >= 400);
    lines.push(
        '',
        'These are forced-GC browser heap/DOM observations after six matched create/clear cycles. They do not measure server session memory, process RSS, peak allocation, GC overhead, or prove absence of leaks. Different framework caches and delegated event systems affect listener/counter totals. Initial and populated-table screenshots are saved under build/comparison-artifacts.',
        '',
        '## Failures, interpretation and limits',
        '',
        `Measured fixture failures: ${result.samples.length - passed.length}; page errors in completed fixtures: ${errors.length}; failed/HTTP>=400 requests: ${failedRequests.length}.`,
        '',
        '```json',
        JSON.stringify(
            result.samples
                .filter((sample) => sample.status !== 'passed')
                .map((sample) => ({implementation: sample.implementation, profile: sample.profile, cache: sample.cache, error: sample.error})),
            null,
            2
        ),
        '```',
        '',
        '- Rankings apply only to these matched server-dependent operations and configurations. The plain DOM implementation is a floor, not a full application framework.',
        '- Vaadin has richer general framework/session infrastructure; Geshra, Flow and React are not globally feature-equivalent. No Grid, themed button, paid component, virtualization, SEO-rendered app or commercial feature is compared against a bare native equivalent.',
        '- Four repeated page samples per condition are small and correlated; per-operation pooled samples are also nested within visits. No confidence-based universal speedup conclusion is supported.',
        '- Different Java/Node server stacks and application implementation choices remain part of the tested systems; this does not isolate library internals. No backend CPU, JVM allocation, per-user server heap, multi-user scalability or server cold-start claim.',
        '- Chrome-only desktop lab; no Firefox/Safari comparison, physical mobile, TLS/DNS WAN, congestion/bandwidth/loss model, energy use, accessibility certification or field-user percentile.',
        '- Initial stylesheet equality and screenshots verify visible fixture parity. Cache behavior and protocol overhead remain real architectural differences rather than being artificially removed.',
        '',
        'Build/run instructions: benchmarks/comparison/README.md. Primary references: [Vaadin Gradle production configuration](https://vaadin.com/docs/latest/flow/configuration/gradle), [Vaadin Element API](https://vaadin.com/docs/latest/flow/component-internals/element-api), [React createRoot](https://react.dev/reference/react-dom/client/createRoot), [React memo](https://react.dev/reference/react/memo), [INP](https://web.dev/articles/inp), [CLS](https://web.dev/articles/cls).',
        ''
    );
    writeFileSync(resolve(root, 'COMPARISON.md'), lines.join('\n'));
    console.log('Wrote performance-results/COMPARISON.md');
}

report();
