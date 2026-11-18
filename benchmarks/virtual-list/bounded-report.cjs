/**
 * Persistent raw evidence and report output.
 */
const {readFileSync, writeFileSync} = require('node:fs');
/**
 * Stable repository-local artifact paths.
 */
const {resolve} = require('node:path');
/**
 * Missing or failed observations never become performance claims.
 */
const assert = require('node:assert/strict');
/**
 * The same documented formulas used by all preceding variant reports.
 */
const {summarize, number, median} = require('./report.cjs');

/**
 * Reads an immutable measurement artifact.
 */
function read(name) {
    return JSON.parse(readFileSync(resolve(__dirname, '../../performance-results/virtual-list/' + name), 'utf8'));
}

/**
 * Computes browser-only counter deltas; instrumentation is included, backend CPU is excluded.
 */
function counterDelta(sample, name) {
    if (!sample.browserCountersInitial) return null;
    const before = sample.browserCountersInitial.metrics.find((metric) => metric.name === name).value;
    const after = sample.browserCountersFinal.metrics.find((metric) => metric.name === name).value;
    return 1000 * (after - before);
}

/**
 * Validates and reports only the final paired cap-32 matrix. Earlier policy measurements remain
 * separate evidence of the bandwidth/latency tradeoff, without pooling browser visits.
 */
function main() {
    const raw = read('bounded.json');
    const inventory = read('inventory-bounded.json');
    const originalSize = read('runtime-sizes.json').before;
    const finalSize = read('runtime-bounded.json');
    const correctness = read('correctness.json');
    assert.equal(raw.samples.length, 48);
    assert.equal(raw.warmups.length, 8);
    assert.ok([...raw.samples, ...raw.warmups].every((sample) => sample.status === 'passed' && sample.errors.length === 0 && sample.count === 100000));
    assert.ok([...raw.samples, ...raw.warmups].every((sample) => sample.sustained.samples.length === 60 && sample.jumps.samples.length === 24));
    assert.equal(new Set(raw.warmups.map((sample) => [sample.engine, sample.profile, sample.version].join('/'))).size, 8);
    assert.ok(raw.warmups.every((sample) => sample.repeat === -1));
    assert.deepEqual(inventory.before.entries, inventory.bounded.entries);
    for (const version of ['before', 'bounded']) assert.equal(raw.artifacts[version], inventory[version].sha256);
    assert.ok(correctness.every((result) => result.deterministic.passed && result.real.passed && result.compatibility.every((entry) => entry.passed)));
    const rows = [];
    for (const engine of ['chrome', 'firefox'])
        for (const profile of ['local', 'delayed'])
            for (const version of ['before', 'bounded']) {
                const samples = raw.samples.filter((sample) => sample.engine === engine && sample.profile === profile && sample.version === version);
                assert.equal(samples.length, 6);
                assert.deepEqual(
                    samples.map((sample) => sample.repeat).sort((left, right) => left - right),
                    [0, 1, 2, 3, 4, 5]
                );
                assert.ok(samples.every((sample) => sample.cpuRate === (engine === 'chrome' && profile === 'delayed' ? 4 : 1)));
                const summary = summarize(samples);
                if (version === 'bounded') assert.ok(summary.maxPending <= 32);
                rows.push({
                    engine,
                    profile,
                    version,
                    ...summary,
                    scriptMs: median(samples.map((sample) => counterDelta(sample, 'ScriptDuration'))),
                    layoutMs: median(samples.map((sample) => counterDelta(sample, 'LayoutDuration')))
                });
            }
    const lines = [
        '# Fixed-height virtualization: final bounded policy',
        '',
        'Geshra keeps its purpose-built constant-space fixed-height range calculation. It won the matched range-only comparison against pinned TanStack virtual-core 3.19.11 without adding a production dependency or per-dataset browser metadata. The final production policy sends changed ranges immediately up to a configurable hard ceiling of 32 outstanding requests per list, then retains only the latest desired range. Smaller limits (1–31) trade less intermediate work for more waiting. No experimental pause timer or jump-priority machinery remains.',
        '',
        'This is a latency-first bound, not a bandwidth optimization or proof that 32 is optimal. The per-list client request ceiling does not bound all server work under arbitrary network conditions or concurrent users. It also does not remove the network round trip, promise smooth arbitrary jumps, support variable-height measurement or establish a universal fastest implementation.',
        '',
        '## Final paired measurements',
        '',
        `48 passing measured cold visits plus eight separate warmups: six repeats × two versions × two browsers × two profiles, with alternating version order within every cohort. All five application class/resource entries are byte-identical between the saved original and final JARs. Logical rows 100,000; 32px row height, 320px list viewport, four-row overscan; browser viewport 1024×768. ${raw.environment.cpu.trim()}, ${raw.environment.logicalCpus} logical CPUs, ${raw.environment.platform}/${raw.environment.arch}, OS ${raw.environment.os}, Node ${raw.environment.node}, Java 21 with -Xms128m/-Xmx512m. Browser versions are recorded per sample. No overlapping build or performance job ran.`,
        '',
        'Local has no added delay. Delayed applies 75ms each direction to both HTTP and WebSockets; Chrome additionally uses CPU rate 4, Firefox uses delay only and CPU rate 1. Values are ordinary six-run medians except worst observed pending/mounted values. No field percentiles or confidence interval is inferred from six visits.',
        '',
        '| Browser / profile | Variant | Initial ready ms | Final-jump settle ms | Viewport requests | Max pending | WS payload down bytes | Provider reads | Max mounted |',
        '| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |'
    ];
    for (const row of rows)
        lines.push(
            `| ${row.engine} / ${row.profile} | ${row.version === 'before' ? 'original' : 'bounded 32'} | ${number(row.ready)} | ${number(row.jumpSettle)} | ${number(row.requests, 0)} | ${row.maxPending} | ${number(row.receivedBytes, 0)} | ${number(row.reads, 0)} | ${row.maxMounted} |`
        );
    lines.push(
        '',
        'Measured costs remain: delayed downstream WS payload increased about 10.5% in Chrome and 9.7% in Firefox. Firefox local readiness rose from 66 to 75ms. No overall speed improvement over the original implementation is established; the selected policy preserves practical final-viewport latency while adding a request bound and correctness protections.',
        '',
        '| Browser / profile | Variant | Frame-gap p95 median ms | Largest sampled gap ms | Uncovered viewport samples % | Request-to-ACK ms | Browser script / layout ms |',
        '| --- | --- | ---: | ---: | ---: | ---: | ---: |'
    );
    for (const row of rows)
        lines.push(
            `| ${row.engine} / ${row.profile} | ${row.version === 'before' ? 'original' : 'bounded 32'} | ${number(row.frameP95)} | ${number(row.maxGap)} | ${number(row.uncovered)} | ${number(row.ackLatency)} | ${number(row.scriptMs)} / ${number(row.layoutMs)} |`
        );
    lines.push(
        '',
        'Final-jump settle runs from the last scripted scroll assignment to the actual final row being inside the viewport plus two animation frames. Frame-gap p95 is a within-visit nearest-rank statistic of 84 scripted RAF samples, then medianed across six visits; it is not compositor FPS or interaction latency. Four-row overscan cannot cover rapid jumps or sustained high-speed scrolling over a 150ms RTT. Uncovered viewport samples are a visible limitation, not hidden by lower request counts. Browser script/layout counters include observation overhead and are not server CPU.',
        '',
        '| Browser / profile | Variant | Post-GC JS heap bytes | Initial-to-final heap change bytes | Post-GC DOM nodes | Long tasks |',
        '| --- | --- | ---: | ---: | ---: | ---: |'
    );
    for (const row of rows)
        lines.push(
            `| ${row.engine} / ${row.profile} | ${row.version === 'before' ? 'original' : 'bounded 32'} | ${number(row.heap, 0)} | ${number(row.heapChange, 0)} | ${number(row.nodes, 0)} | ${number(row.longTasks, 0)} |`
        );
    lines.push(
        '',
        'Heap/DOM snapshots are Chrome-only, include document/runtime/instrumentation, and are not backend-memory or leak proofs. Recorded request/ACK/long-task arrays were copied out and cleared before final GC. Firefox heap and unsupported long-task data are unavailable. Lazy provider reads measure data-access volume; ACK latency combines network and server generation/serialization/delivery. No isolated backend CPU/allocation/assembly claim is made.',
        '',
        '## Payload and policy costs',
        '',
        `Actual full runtime: ${originalSize.raw} → ${finalSize.raw} raw bytes, ${originalSize.gzip} → ${finalSize.gzip} gzip bytes (+${finalSize.gzip - originalSize.gzip} gzip bytes). Added logical-key and request-identity metadata can increase workload traffic. WS totals count message payloads, excluding framing/TCP/TLS. Raw initial HTTP observations cover assets, excluding the HTML document, so no total-wire-byte claim is made. Compression settings are unchanged.`,
        '',
        'The preserved one-request matrix reduced delayed work and bytes but substantially delayed the final viewport. Immediate two-slot and reserved-slot experiments also left latency costs. These separate artifacts explain the final preference for immediate bounded dispatch; their visits are not pooled into this paired comparison. Limits below 32 remain explicit application configuration choices. The final cap is justified for this workload, not asserted as a universal optimum.',
        '',
        '## Correctness and provenance',
        '',
        'The lazy indexed provider avoids materializing the whole dataset on either side. Overlapping rows retain component identity and native input selection. Logical keys are separate from component IDs; refresh rebuilds current content/callbacks. Eviction hands focus to the list before sending the request and makes outgoing rows inert. Pending-range intersections protect rows any outstanding request may dispose. All structural DOM batches remain ordered and applied; tokens prevent stale/repeated ACKs from freeing another request. Durable edits/selection belong to the application model. Arbitrary Components are not reassigned through a DOM pool.',
        '',
        '59 Java tests cover lazy replacement, stale revisions, bounded keys, provider/attachment failures and optional legacy/compact request identities. Chrome/Firefox dedicated tests cover cap-32 saturation, latest-range release, refresh/reconnect from saturation, disposal releasing request records, partial failure alongside another pending request, repeated ranges with different tokens, old/new deployment combinations, focus/selection and durable edits. Final consumer and complete browser-regression results accompany delivery.',
        '',
        `Actual measured baseline: build/virtual-list-before.jar, SHA-256 ${inventory.before.sha256}. Final: build/virtual-list-bounded.jar, SHA-256 ${inventory.bounded.sha256}. inventory-bounded.json verifies fixture byte identity; source-bounded.json and runtime-bounded.json record measurement-time source/runtime hashes. Earlier source snapshots belong to their explicitly named intermediate variants. Saved binary hashes identify the actual measured artifacts even if documentation changes afterward.`,
        '',
        'Reproduce with [benchmark README](../../benchmarks/virtual-list/README.md). Range-only selection and upstream license: [range evidence](VIRTUAL-LIST.md), [pinned TanStack source](https://github.com/TanStack/virtual/tree/11b48e4ab8b08d483c8fe29a31ef11261ffbda04/packages/virtual-core), [MIT notice](../../benchmarks/virtual-list/THIRD_PARTY_NOTICES.md). Earlier policy evidence: [immediate two-slot](VIRTUAL-LIST-LATENCY.md), [reserved slot](VIRTUAL-LIST-PRIORITY.md). Existing unvirtualized four-app results are unchanged. Variable-height rows, touch inertia, WAN bandwidth/loss, multiuser server load and unrelated JS APIs require separate measurements. No commits, pushes, deployment or new CI actions are part of this pass.'
    );
    writeFileSync(resolve(__dirname, '../../performance-results/virtual-list/bounded-summary.json'), JSON.stringify(rows, null, 2) + '\n');
    writeFileSync(resolve(__dirname, '../../performance-results/virtual-list/FINAL.md'), lines.join('\n') + '\n');
    console.log('Validated final 48 visits, 8 warmups, fixture identity and cap');
}

main();
