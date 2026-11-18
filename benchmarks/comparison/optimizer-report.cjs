/**
 * Generates the bounded startup/cache optimization report from preserved paired evidence.
 */
const {readFileSync, writeFileSync} = require('node:fs');
/**
 * Resolves retained evidence without changing the original competitor comparison artifacts.
 */
const {resolve} = require('node:path');

/**
 * Computes ordinary sample median and nearest-rank p95; unavailable values remain null.
 */
function stats(values) {
    const sorted = values.filter(Number.isFinite).sort((left, right) => left - right);
    const middle = Math.floor(sorted.length / 2);
    return {
        n: sorted.length,
        median: sorted.length ? (sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2) : null,
        p95: sorted.length ? sorted[Math.ceil(sorted.length * 0.95) - 1] : null
    };
}

/**
 * Formats finite metrics without replacing unsupported or missing measurements with zero.
 */
function number(value, digits = 1) {
    return Number.isFinite(value) ? value.toFixed(digits) : 'unavailable';
}

/**
 * Formats a metric's median/p95 and actual sample count.
 */
function pair(values, digits = 1) {
    const result = stats(values);
    return `${number(result.median, digits)} / ${number(result.p95, digits)} (${result.n})`;
}

/**
 * Checks paired environments, complete correctness, explicit delay settings and unchanged cohorts.
 */
function validate(before, after) {
    for (const field of ['browser', 'node', 'cpu', 'platform', 'arch'])
        if (before.environment[field] !== after.environment[field]) throw new Error('Environment mismatch: ' + field);
    for (const field of ['profiles', 'viewport', 'implementations', 'repeats'])
        if (JSON.stringify(before[field]) !== JSON.stringify(after[field])) throw new Error('Cohort mismatch: ' + field);
    for (const run of [before, after]) {
        if (!run.completedAt || run.samples.length !== 16 || run.warmups.length !== 2) throw new Error('Expected complete 16-case Geshra run and two warmups');
        for (const sample of [...run.samples, ...run.warmups]) {
            if (sample.status !== 'passed' || sample.errors.length) throw new Error('Failed fixture or page error');
            const profile = run.profiles.find((profile) => profile.name === sample.profile);
            if (sample.appliedDelayMs !== profile.delayEachWayMs) throw new Error('Unchecked delay');
            for (const operation of sample.operations)
                if (operation.operation === 'Select 500' && operation.correctness.rows !== 1000) throw new Error('Selection postcondition failed');
        }
    }
    if (!before.runtimeArtifactSha256 || !after.runtimeArtifactSha256 || before.runtimeArtifactSha256 === after.runtimeArtifactSha256)
        throw new Error('Missing distinct measured artifact hashes');
}

/**
 * Writes measured gains, costs and limits without reclassifying old competitor results as reruns.
 */
function report() {
    const directory = resolve(__dirname, '../../performance-results/optimizer-pass');
    const before = JSON.parse(readFileSync(resolve(directory, 'before-fresh.json'), 'utf8'));
    const after = JSON.parse(readFileSync(resolve(directory, 'after-fresh.json'), 'utf8'));
    const compression = JSON.parse(readFileSync(resolve(directory, 'compression-jmh.json'), 'utf8'));
    const fixture = JSON.parse(readFileSync(resolve(directory, 'compression-fixture.json'), 'utf8'));
    validate(before, after);
    const lines = [
        '# Startup and browser-cache optimization pass',
        '',
        'This bounded pass changes production cache validation and initial-route bootstrap. It preserves the original matched competitor comparison and fixtures, and measures the saved baseline JAR against the candidate. No production compression or bulk DOM rewrite is included.',
        '',
        `Machine: ${after.environment.cpu.trim()}, ${after.environment.platform}/${after.environment.arch}; Chrome ${after.environment.browser}; Node ${after.environment.node}; Java 21 for both application JARs and JMH.`,
        '',
        `Baseline runtime artifact SHA-256: \`${before.runtimeArtifactSha256}\`. Candidate: \`${after.runtimeArtifactSha256}\`. Full inventories and source hashes are retained beside this report.`,
        '',
        '## Shipped changes',
        '',
        '- Immutable packaged non-HTML assets receive precomputed weak SHA-256 validators, browser-private `no-cache` policy, `Vary: Accept-Encoding`, and bodyless 304 responses. Hashing happens once on cache insertion, not on a cache hit. A changed deployed body changes its tag; pages, session responses, live files, errors and streamed content remain `no-store`. No long-lived freshness or stale-asset promise is introduced.',
        '- New documents advertise bootstrap support. A new client can include a bounded initial path and explicit compact-reader capability during the same-origin/session-validated WebSocket handshake. The server renders immediately through the exact built-in route path, acknowledges before DOM output, and the client omits the otherwise redundant route request.',
        '- Custom route handlers retain their original packet contract. Changed paths during handshake are navigated. Rendering failures signal an explicit 1011 recovery close; the client rebuilds through plain `/ws` and drops queued events with stale component IDs. Long paths and legacy/rolling-downgrade servers retain plain-socket negotiation and timeout recovery.',
        '',
        '## Matched before/after conditions',
        '',
        'Each version has 16 measured visits: local and 75 ms-per-direction application-stream delay / CPU 4 profiles, cold and warm browser cache, four repetitions per condition, plus one excluded complete workload warmup per profile. Same production fixture, visible native controls/CSS, 1,000 rows, operation sequence, browser, viewport and TCP relay implementation. The relay applies delay to both HTTP and WebSocket traffic and verifies its applied setting before/after each visit. Server/JIT caches remain warm; cold means browser cache, not server cold start.',
        '',
        'Runs were sequential **baseline→candidate**, not counterbalanced across versions. CPU frequency, thermal state and background drift were not controlled. Four-sample p95 is the observed maximum, and tiny local timing differences are not established speedups. Large controlled-delay effects and deterministic byte/body reuse are the stronger evidence. Competitor builds/settings were not changed or rerun for this pass.',
        '',
        'Values below are median / nearest-rank p95 (count). Readiness is complete matched content plus two animation frames, not display scanout or field Web Vitals.',
        '',
        '| Profile | Cache | Ready before ms | Ready after ms | Median change | Initial downstream before bytes | After bytes |',
        '| --- | --- | ---: | ---: | ---: | ---: | ---: |'
    ];
    for (const profile of before.profiles)
        for (const cache of ['cold', 'warm']) {
            const baseline = before.samples.filter((sample) => sample.profile === profile.name && sample.cache === cache);
            const candidate = after.samples.filter((sample) => sample.profile === profile.name && sample.cache === cache);
            const previous = stats(baseline.map((sample) => sample.readyMs)).median;
            const current = stats(candidate.map((sample) => sample.readyMs)).median;
            lines.push(
                `| ${profile.name} | ${cache} | ${pair(baseline.map((sample) => sample.readyMs))} | ${pair(candidate.map((sample) => sample.readyMs))} | ${number((current / previous - 1) * 100)}% | ${pair(
                    baseline.map((sample) => sample.loadBytes.serverToClient),
                    0
                )} | ${pair(
                    candidate.map((sample) => sample.loadBytes.serverToClient),
                    0
                )} |`
            );
        }
    lines.push(
        '',
        'Initial stream bytes include HTTP headers and WebSocket bootstrap, excluding TCP/IP/TLS and close traffic. Cache validators still require network validation; this does not achieve fingerprinted-asset zero-request warm startup.',
        '',
        '## Rendering, body reuse and workload costs',
        '',
        '| Profile | Cache | LCP before/after ms medians | CLS before/after medians | HTTP304 responses before/after | Initial Script before/after ms medians |',
        '| --- | --- | ---: | ---: | ---: | ---: |'
    );
    for (const profile of before.profiles)
        for (const cache of ['cold', 'warm']) {
            const baseline = before.samples.filter((sample) => sample.profile === profile.name && sample.cache === cache);
            const candidate = after.samples.filter((sample) => sample.profile === profile.name && sample.cache === cache);
            lines.push(
                `| ${profile.name} | ${cache} | ${number(stats(baseline.map((sample) => sample.load.lcpMs)).median)} / ${number(stats(candidate.map((sample) => sample.load.lcpMs)).median)} | ${number(stats(baseline.map((sample) => sample.metrics.cls)).median, 4)} / ${number(stats(candidate.map((sample) => sample.metrics.cls)).median, 4)} | ${baseline.flatMap((sample) => sample.loadNetwork.requests).filter((request) => request.status === 304).length} / ${candidate.flatMap((sample) => sample.loadNetwork.requests).filter((request) => request.status === 304).length} | ${number(stats(baseline.map((sample) => sample.loadCpu.ScriptDuration * 1000)).median)} / ${number(stats(candidate.map((sample) => sample.loadCpu.ScriptDuration * 1000)).median)} |`
            );
        }
    lines.push(
        '',
        'Chrome CDP may expose a successfully revalidated cached response as 200; raw body transfer sizes and retained cache flags are also available. Zero CLS is limited to this scripted observation. CDP script counters include the measured orchestration window and do not isolate server CPU.',
        '',
        '| Profile | Operation | Before DOM ms | After DOM ms |',
        '| --- | --- | ---: | ---: |'
    );
    for (const profile of before.profiles)
        for (const operation of ['Increment', 'Create 1000', 'Update tenth', 'Clear']) {
            lines.push(
                `| ${profile.name} | ${operation} | ${pair(before.samples.filter((sample) => sample.profile === profile.name).flatMap((sample) => sample.operations.filter((item) => item.operation === operation).map((item) => item.mutationMs)))} | ${pair(after.samples.filter((sample) => sample.profile === profile.name).flatMap((sample) => sample.operations.filter((item) => item.operation === operation).map((item) => item.mutationMs)))} |`
            );
        }
    lines.push('', '| Profile | Whole-workload upstream before/after bytes medians | Downstream before/after bytes medians |', '| --- | ---: | ---: |');
    for (const profile of before.profiles) {
        const baseline = before.samples.filter((sample) => sample.profile === profile.name);
        const candidate = after.samples.filter((sample) => sample.profile === profile.name);
        lines.push(
            `| ${profile.name} | ${number(stats(baseline.map((sample) => sample.operationsBytes.clientToServer)).median, 0)} / ${number(stats(candidate.map((sample) => sample.operationsBytes.clientToServer)).median, 0)} | ${number(stats(baseline.map((sample) => sample.operationsBytes.serverToClient)).median, 0)} / ${number(stats(candidate.map((sample) => sample.operationsBytes.serverToClient)).median, 0)} |`
        );
    }
    lines.push(
        '',
        'Bulk downstream UI mutation bytes are a remaining bottleneck. These changes do not compress row commands or promise a bulk-render speedup. Ordinary operation timing shifts must be read with the sequential-run limitation above.',
        '',
        '## Compression assessment: measured, not enabled',
        '',
        'Separate JMH fixture: production DOM serialization for 1,000 native two-cell rows, 53,495 bytes in one compact frame. This is a separate constructed row fixture, not the exact matched app (which includes additional row attributes). Raw DEFLATE resets context per frame, reuses output/native compressor state, and excludes initial component construction. Two fresh forks, 3×500 ms warmup, 5×500 ms measurement, GC profiler, Java 21. No browser workload ran during the retained isolated compression measurement.',
        '',
        '| Level | Mean µs/group | JMH error µs | Measured Java allocation B/group | Compressed fixture bytes |',
        '| --- | ---: | ---: | ---: | ---: |'
    );
    for (const entry of compression)
        lines.push(
            `| ${entry.params.level} | ${number(entry.primaryMetric.score, 2)} | ${number(entry.primaryMetric.scoreError, 2)} | ${number(entry.secondaryMetrics['gc.alloc.rate.norm'].score, 2)} | ${fixture.levels[entry.params.level].compressedBytes} |`
        );
    lines.push(
        '',
        'Level 1 saves most of the fixture bytes at much lower CPU cost than level 6. This does not establish an end-to-end WebSocket implementation benefit: native compressor memory, context creation, browser inflate, fragmented-message handling, selective control-frame policy, backpressure and concurrent-session behavior were not measured. Small GC-profiler B/op readings are steady-state measurements, not a zero-native-memory claim. Global compression remains disabled; no new production dependency or per-connection compression context was added.',
        '',
        '## Verification and remaining limits',
        '',
        '- 54 library tests and 2 Spring consumer tests; Chrome and Firefox protocol/native-control/auth suites; explicit ETag/gzip/HEAD/304/deployment-change checks; real-server initial bootstrap, path-change recovery, acknowledged render failure and old-server fallback. The recorded run commands and final audit disposition accompany delivery.',
        '- Original four-app, 64-visit competitor artifacts remain separate; this pass compares only the saved Geshra baseline and candidate using the unchanged matched fixture. No new ranking against competitors is established.',
        '- No backend allocation/heap/assembly claim for startup/cache changes; they primarily remove network work. Native compression state and browser decoding remain unmeasured.',
        '- Warm assets still revalidate; further improvement would need safe content-addressed resource URLs. Bulk creation, row-command volume, sustained receive backpressure, physical devices, WAN bandwidth/loss, TLS/DNS, multiple users and field percentiles remain outside this bounded pass.',
        '',
        '## Reproduce',
        '',
        'Provenance: source-after.json is the measurement-time source snapshot. Subsequent source edits changed documentation and Javadocs only; the minified runtime bundle is unchanged. The preserved candidate JAR and its recorded SHA-256 identify the actual measured binary.',
        '',
        'Preserved baseline JAR: build/optimizer-baseline/matched-geshra.jar. Preserved measured candidate: build/optimizer-candidate/matched-geshra.jar. Run the existing matched harness with COMPARISON_APPS=geshra and select the saved/current backend using COMPARISON_GESHRA_BACKEND when starting proxy.cjs. Each raw run records COMPARISON_LABEL and the SHA-256 of COMPARISON_ARTIFACT; select distinct COMPARISON_OUTPUT paths. Build/server commands remain in benchmarks/comparison/README.md.',
        '',
        'Compression: `./gradlew benchmark -PbenchmarkInclude=geshra.benchmark.CompressionBenchmark.* -PbenchmarkOutput=performance-results/optimizer-pass/compression-jmh.json` (PowerShell quotes each -P argument; Windows uses gradlew.bat). Correctness: browser-tests npm test; BASELINE_URL=http://localhost:4111 enables saved-old-server checks.',
        '',
        'HTTP semantics: [RFC 9110 conditional requests](https://www.rfc-editor.org/rfc/rfc9110.html), [RFC 9111 browser/private caching](https://www.rfc-editor.org/rfc/rfc9111.html).',
        ''
    );
    writeFileSync(resolve(directory, 'OPTIMIZATION.md'), lines.join('\n'));
    console.log('Wrote optimizer-pass/OPTIMIZATION.md');
}

report();
