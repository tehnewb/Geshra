/**
 * Reads only retained final evidence; pilot and preliminary adapter results are excluded.
 */
const {readFileSync, writeFileSync} = require('node:fs');
/**
 * Stable repository-local artifact paths.
 */
const {resolve} = require('node:path');
/**
 * Report validation never assigns success to missing or failed observations.
 */
const assert = require('node:assert/strict');

/**
 * Reads one preserved raw artifact.
 */
function read(name) {
    return JSON.parse(readFileSync(resolve(__dirname, '../../performance-results/virtual-list/' + name), 'utf8'));
}

/**
 * Uses the ordinary midpoint median for even cohorts; null means unavailable, never zero.
 */
function median(values) {
    const sorted = values.filter((value) => value !== null && value !== undefined).sort((a, b) => a - b);
    if (!sorted.length) return null;
    return (sorted[Math.floor((sorted.length - 1) / 2)] + sorted[Math.floor(sorted.length / 2)]) / 2;
}

/**
 * Returns a nearest-rank percentile of an individual run's frame/ACK observations.
 */
function percentile(values, fraction) {
    if (!values.length) return null;
    const sorted = [...values].sort((a, b) => a - b);
    return sorted[Math.max(0, Math.ceil(sorted.length * fraction) - 1)];
}

/**
 * Formats measured values without implying precision below a tenth of a millisecond.
 */
function number(value, digits = 1) {
    return value === null || value === undefined ? 'unavailable' : value.toFixed(digits);
}

/**
 * Aggregates a four-visit cohort while preserving the raw trace for every derived statistic.
 */
function summarize(samples) {
    return {
        ready: median(samples.map((sample) => sample.initial.readyMs)),
        requests: median(samples.map((sample) => sample.metrics.requests.length)),
        maxPending: Math.max(...samples.map((sample) => sample.metrics.maxPending)),
        receivedBytes: median(samples.map((sample) => sample.metrics.receivedBytes)),
        sentBytes: median(samples.map((sample) => sample.metrics.sentBytes)),
        reads: median(samples.map((sample) => Number(sample.serverStats.match(/reads=(\d+)/)[1]))),
        maxMounted: Math.max(...samples.flatMap((sample) => [...sample.sustained.samples, ...sample.jumps.samples].map((frame) => frame.mounted))),
        jumpSettle: median(samples.map((sample) => sample.jumps.settledAt - sample.jumps.endedAt)),
        frameP95: median(
            samples.map((sample) =>
                percentile(
                    [...sample.sustained.samples, ...sample.jumps.samples].map((frame) => frame.gap),
                    0.95
                )
            )
        ),
        maxGap: Math.max(...samples.flatMap((sample) => [...sample.sustained.samples, ...sample.jumps.samples].map((frame) => frame.gap))),
        uncovered: median(samples.map((sample) => (100 * [...sample.sustained.samples, ...sample.jumps.samples].filter((frame) => frame.uncoveredPixels > 1).length) / 84)),
        ackLatency: median(samples.map((sample) => median(sample.metrics.acknowledgments.map((ack) => ack.latencyMs)))),
        heap: median(samples.map((sample) => sample.heapFinal?.usedSize)),
        heapChange: median(samples.map((sample) => (sample.heapFinal ? sample.heapFinal.usedSize - sample.heapInitial.usedSize : null))),
        nodes: median(samples.map((sample) => sample.domFinal?.nodes)),
        longTasks: median(samples.map((sample) => (sample.metrics.longTaskSupported ? sample.metrics.longTasks.length : null)))
    };
}

/**
 * Validates the complete matrix and writes the report plus machine-readable cohort aggregates.
 */
function main() {
    const browser = read('browser.json');
    const ranges = read('range.json');
    const inventory = read('inventory.json');
    const sizes = read('runtime-sizes.json');
    const correctness = read('correctness-one-flight.json');
    assert.equal(browser.samples.length, 64);
    assert.equal(browser.warmups.length, 16);
    assert.ok([...browser.samples, ...browser.warmups].every((sample) => sample.status === 'passed' && !sample.errors.length));
    assert.equal(ranges.samples.length, 48);
    assert.ok(ranges.samples.every((sample) => sample.configurationChecks === 90));
    assert.deepEqual(inventory.before.entries, inventory.after.entries);
    assert.ok(correctness.every((entry) => entry.deterministic.passed && entry.real.passed && entry.compatibility.every((combination) => combination.passed)));
    const lines = [
        '# Fixed-height virtual-list results: preserved one-request variant',
        '',
        'This historical intermediate variant is retained alongside its raw evidence. The current cap-32 default and final paired measurements are in [FINAL.md](FINAL.md). The range-only candidate comparison below remains the selection evidence for the native fixed-height calculation.',
        '',
        'The production implementation retains the simple constant-space fixed-height range calculation. It adds one outstanding viewport request plus the latest desired range, request identities across reconnects, bounded failure recovery, explicit logical keys/lazy provider replacement and pre-send focus/inert protection. No third-party virtualizer or unrelated JS API enters the production bundle. This finite workload does not establish a universal fastest implementation.',
        '',
        '## Range subsystem selection',
        '',
        'Pinned TanStack virtual-core 3.19.11 versus the actual production range function; identical minifier/harness, reused adapter geometry, constant configuration during timing, exact ranges and 90 changed-configuration checks afterward. Six fresh contexts per browser/count/candidate, alternating order; 1,000 warmup evaluations, then 200,000 timed evaluations. Geometry is supplied directly to both range subsystems; this is not the complete TanStack observer/renderer lifecycle. Small native initialization times are at browser timer resolution. Transient allocation rates were not profiled.',
        '',
        '| Browser | Logical rows | Native loop median ms | Core loop median ms | Native / core post-GC JS delta bytes | Native / core numeric backing delta bytes |',
        '| --- | ---: | ---: | ---: | ---: | ---: |'
    ];
    for (const engine of ['chrome', 'firefox'])
        for (const count of [10000, 100000]) {
            const native = ranges.samples.filter((sample) => sample.engine === engine && sample.count === count && sample.candidate === 'fixed');
            const core = ranges.samples.filter((sample) => sample.engine === engine && sample.count === count && sample.candidate === 'tanstack');
            assert.equal(native.length, 6);
            assert.equal(core.length, 6);
            lines.push(
                `| ${engine} | ${count} | ${number(median(native.map((sample) => sample.measured.loopMs)))} | ${number(median(core.map((sample) => sample.measured.loopMs)))} | ${number(median(native.map((sample) => sample.retainedHeapDelta)), 0)} / ${number(median(core.map((sample) => sample.retainedHeapDelta)), 0)} | ${number(median(native.map((sample) => sample.retainedBackingDelta)), 0)} / ${number(median(core.map((sample) => sample.retainedBackingDelta)), 0)} |`
            );
        }
    lines.push('', 'Candidate bundle bytes include identical measurement/configuration-check code and retained upstream notices; they are not incremental production payloads.');
    for (const candidate of ranges.candidates) lines.push(`- ${candidate.name}: ${candidate.bytes} raw bytes, ${candidate.gzipBytes} gzip bytes; SHA-256 ${candidate.sha256}.`);
    lines.push(
        '',
        `Actual full runtime: ${sizes.before.raw} → ${sizes.after.raw} raw bytes; ${sizes.before.gzip} → ${sizes.after.gzip} gzip bytes. The additional recovery/identity behavior costs ${sizes.after.gzip - sizes.before.gzip} gzip bytes. No compression setting changed.`,
        '',
        '## Real production fixture',
        '',
        `Environment: ${browser.environment.cpu}, ${browser.environment.logicalCpus} logical CPUs, ${browser.environment.platform}/${browser.environment.arch}, OS ${browser.environment.os}, Node ${browser.environment.node}, Java 21. Browser versions are retained per sample. Five fixture class/resource entries match byte-for-byte across the two preserved JARs. 64 passing measured cold visits plus 16 separate warmups; four repeats per cohort, alternating version order within every cohort. Browser viewport 1024×768; list viewport 320px, row height 32px, overscan four on each side. Delayed means 75ms each direction for both HTTP and WS; Chrome CPU rate 4, Firefox CPU rate 1. Local CPU rate 1. No parallel build/benchmark ran.`,
        '',
        'Values below are ordinary four-run medians except maximum pending/mounted values, which are worst observed across the cohort. Four observations do not establish field p95 or confidence intervals.',
        '',
        '| Browser / profile / rows | Ready ms before → after | Viewport requests before → after | Max pending before → after | WS payload down bytes before → after | Provider reads before → after | Max mounted before → after |',
        '| --- | ---: | ---: | ---: | ---: | ---: | ---: |'
    );
    const cohorts = [];
    for (const engine of ['chrome', 'firefox'])
        for (const profile of ['local', 'delayed'])
            for (const count of [10000, 100000]) {
                const pair = {};
                for (const version of ['before', 'after']) {
                    const samples = browser.samples.filter(
                        (sample) => sample.engine === engine && sample.profile === profile && sample.count === count && sample.version === version
                    );
                    assert.equal(samples.length, 4);
                    assert.ok(samples.every((sample) => sample.cpuRate === (engine === 'chrome' && profile === 'delayed' ? 4 : 1)));
                    pair[version] = summarize(samples);
                }
                const {before, after} = pair;
                assert.ok(after.maxPending <= 1);
                cohorts.push({engine, profile, count, ...pair});
                lines.push(
                    `| ${engine} / ${profile} / ${count} | ${number(before.ready)} → ${number(after.ready)} | ${number(before.requests, 0)} → ${number(after.requests, 0)} | ${before.maxPending} → ${after.maxPending} | ${number(before.receivedBytes, 0)} → ${number(after.receivedBytes, 0)} | ${number(before.reads, 0)} → ${number(after.reads, 0)} | ${before.maxMounted} → ${after.maxMounted} |`
                );
            }
    lines.push(
        '',
        '| Browser / profile / rows | Last jump settle ms before → after | Frame-gap p95 median ms before → after | Max sampled gap ms before → after | Samples with uncovered viewport % before → after | Request-to-ACK ms before → after | Long tasks before → after |',
        '| --- | ---: | ---: | ---: | ---: | ---: | ---: |'
    );
    for (const {engine, profile, count, before, after} of cohorts)
        lines.push(
            `| ${engine} / ${profile} / ${count} | ${number(before.jumpSettle)} → ${number(after.jumpSettle)} | ${number(before.frameP95)} → ${number(after.frameP95)} | ${number(before.maxGap)} → ${number(after.maxGap)} | ${number(before.uncovered)} → ${number(after.uncovered)} | ${number(before.ackLatency)} → ${number(after.ackLatency)} | ${number(before.longTasks, 0)} → ${number(after.longTasks, 0)} |`
        );
    lines.push(
        '',
        'Frame-gap p95 is calculated within each 84-sample scripted-scroll visit, then medianed across four visits; it is not interaction latency or compositor FPS. Uncovered pixels sample viewport coverage while the real network response is pending. A four-row overscan cannot cover arbitrary jumps or sustained high-speed scrolling across a 150ms RTT. Bounded queued work does not remove that network floor or establish smoother scrolling.',
        '',
        '| Browser / profile / rows | Post-GC JS heap bytes before → after | Initial-to-final heap delta bytes before → after | Post-GC DOM nodes before → after |',
        '| --- | ---: | ---: | ---: |'
    );
    for (const {engine, profile, count, before, after} of cohorts)
        lines.push(
            `| ${engine} / ${profile} / ${count} | ${number(before.heap, 0)} → ${number(after.heap, 0)} | ${number(before.heapChange, 0)} → ${number(after.heapChange, 0)} | ${number(before.nodes, 0)} → ${number(after.nodes, 0)} |`
        );
    lines.push(
        '',
        'Heap/DOM probes are Chrome-only retained-state snapshots, include document/runtime/observer overhead, and are not backend memory or leak proofs. Raw trace arrays were copied to results then cleared before final GC. Lazy provider reads separate data-access volume from mounted DOM; ACK latency still combines network, server generation/serialization and delivery. No isolated backend CPU or allocation claim is made. Raw initial resource observations cover HTTP assets and WS payloads; HTML document/framing/TCP/TLS are excluded, so these are not claimed as total wire bytes.',
        '',
        '## Correctness, provenance and limits',
        '',
        '59 Java tests include provider failures, attachment cleanup, duplicate live keys, lazy replacement, stale revisions, optional token grammar and old-client input. Chrome and Firefox separately pass focus/selection, durable edits, reconnect, disposal, mixed old/new deployment combinations and deterministic initial/request/stale-token/failure/retained-list interleavings. Overlap retains DOM identity; eviction hands focus to the list and disposes rows. Arbitrary components are not reassigned to another record through a DOM pool: that would need a separate renderer/update contract to preserve callbacks and input semantics.',
        '',
        `Baseline JAR SHA-256: ${inventory.before.sha256}. Candidate JAR SHA-256: ${inventory.after.sha256}. Actual files: build/virtual-list-before.jar and build/virtual-list-after.jar. source-after.json is the measurement-time source snapshot; inventory.json verifies fixture byte identity. runtime-sizes.json records the actual bundle hashes. Pilot and earlier adapter diagnostic files are excluded. No commits, pushes, deployment or new CI actions are part of this pass.`,
        '',
        'The result applies to fixed-height rows on these two browser builds and this deterministic single-session workload. Variable-height measurement, pooling arbitrary Components, mobile/touch scrolling, backend throughput/allocation/assembly, concurrent users, WAN bandwidth/loss and other JS APIs need separate work and benchmarks. The existing four-app unvirtualized comparison remains unchanged.',
        '',
        'Reproduction commands and metric definitions: [benchmark README](../../benchmarks/virtual-list/README.md). Upstream source: [pinned TanStack core](https://github.com/TanStack/virtual/tree/11b48e4ab8b08d483c8fe29a31ef11261ffbda04/packages/virtual-core), [MIT license](../../benchmarks/virtual-list/THIRD_PARTY_NOTICES.md).'
    );
    writeFileSync(resolve(__dirname, '../../performance-results/virtual-list/summary.json'), JSON.stringify(cohorts, null, 2) + '\n');
    writeFileSync(resolve(__dirname, '../../performance-results/virtual-list/VIRTUAL-LIST.md'), lines.join('\n') + '\n');
    console.log('Validated 64 browser visits, 16 warmups, 48 range samples and compatibility evidence');
}

if (require.main === module) main();

module.exports = {summarize, median, number};
