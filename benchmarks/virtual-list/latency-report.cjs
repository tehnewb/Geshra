/**
 * Preserved evidence and generated report output.
 */
const {readFileSync, writeFileSync} = require('node:fs');
/**
 * Actual local artifact paths.
 */
const {resolve} = require('node:path');
/**
 * Full-matrix and identity validation precede interpretation.
 */
const assert = require('node:assert/strict');
/**
 * Identical summary formulas to the original 64-visit variant report.
 */
const {summarize, number} = require('./report.cjs');

/**
 * Reads one retained artifact from the shared evidence directory.
 */
function read(name) {
    return JSON.parse(readFileSync(resolve(__dirname, '../../performance-results/virtual-list/' + name), 'utf8'));
}

/**
 * Reports the latency-driven policy follow-up without replacing or pooling the original matrix.
 */
function main() {
    const raw = read('latency.json');
    const inventory = read('inventory-two-flight.json');
    const sizes = read('runtime-sizes.json');
    const twoSize = read('runtime-two-flight.json');
    const correctness = read('correctness-two-flight.json');
    assert.equal(raw.samples.length, 72);
    assert.equal(raw.warmups.length, 12);
    assert.ok([...raw.samples, ...raw.warmups].every((sample) => sample.status === 'passed' && sample.errors.length === 0 && sample.count === 100000));
    for (const version of ['before', 'after', 'two-flight']) {
        assert.equal(raw.artifacts[version], inventory[version].sha256);
        assert.deepEqual(inventory[version].entries, inventory.before.entries);
    }
    assert.ok(correctness.every((result) => result.deterministic.passed && result.real.passed && result.compatibility.every((entry) => entry.passed)));
    const rows = [];
    for (const engine of ['chrome', 'firefox'])
        for (const profile of ['local', 'delayed'])
            for (const version of ['before', 'after', 'two-flight']) {
                const samples = raw.samples.filter((sample) => sample.engine === engine && sample.profile === profile && sample.version === version);
                assert.equal(samples.length, 6);
                const result = summarize(samples);
                if (version !== 'before') assert.ok(result.maxPending <= (version === 'after' ? 1 : 2));
                rows.push({engine, profile, version, ...result});
            }
    const lines = [
        '# Fixed-height virtual list: latency follow-up',
        '',
        'This is the preserved immediate two-slot experiment, not the current production policy. See [FINAL.md](FINAL.md) for the selected cap-32 default and final paired matrix.',
        '',
        'The constant-space native fixed-height range function remains the selected implementation; the separate fair core comparison is in the original report. The initial one-request policy saved work but added a substantial wait after the final jump. This follow-up compares the unchanged original, preserved one-request variant and bounded two-request candidate on the same actual 100,000-row production fixture. Earlier 64 visits and 48 range-subsystem samples remain separate and are not pooled into these results.',
        '',
        'The production API exposes `setMaximumPendingRequests(1)` or `(2)`. Two is a bounded pipeline: the browser can send a newer range while one older response is traveling, with only the latest desired range retained after both slots fill. One favors lower traffic. Tokens identify requests across repeated ranges, reconnect and reattachment; all structural DOM batches still apply in server order. Focus and inert protection account for every outstanding eviction. Off-window arbitrary Components are disposed rather than reassigned to another record; application state owns durable edits/selection. Fixed heights only.',
        '',
        '## Matched measurements',
        '',
        '72 passing measured cold visits plus 12 separate warmups: six repeats × three variants × two browsers × two profiles. All three fixture class/resource sets are byte-identical. Six orders rotate all three positions, then reverse direction, within every browser/profile cohort. No overlapping performance or build jobs ran. List viewport 320px, rows 32px, overscan four; browser viewport 1024×768. Local has no artificial delay; delayed applies 75ms each direction equally to HTTP and WebSockets. Chrome additionally uses CPU rate 4 for delayed; Firefox has delay only and CPU rate 1. Values are ordinary six-run medians, except worst observed pending/mounted values. This small deterministic sample does not establish field percentiles.',
        '',
        '| Browser / profile | Variant | Initial ready ms | Final-jump settle ms | Viewport requests | Max pending | WS payload down bytes | Provider reads | Max mounted |',
        '| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |'
    ];
    for (const row of rows)
        lines.push(
            `| ${row.engine} / ${row.profile} | ${row.version === 'before' ? 'original' : row.version === 'after' ? 'one request' : 'two requests'} | ${number(row.ready)} | ${number(row.jumpSettle)} | ${number(row.requests, 0)} | ${row.maxPending} | ${number(row.receivedBytes, 0)} | ${number(row.reads, 0)} | ${row.maxMounted} |`
        );
    lines.push(
        '',
        '| Browser / profile | Variant | Sampled frame-gap p95 median ms | Largest sampled gap ms | Uncovered viewport samples % | Request-to-ACK ms | Post-GC JS heap bytes | Long tasks |',
        '| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |'
    );
    for (const row of rows)
        lines.push(
            `| ${row.engine} / ${row.profile} | ${row.version === 'before' ? 'original' : row.version === 'after' ? 'one request' : 'two requests'} | ${number(row.frameP95)} | ${number(row.maxGap)} | ${number(row.uncovered)} | ${number(row.ackLatency)} | ${number(row.heap, 0)} | ${number(row.longTasks, 0)} |`
        );
    lines.push(
        '',
        'Final-jump settle is measured from the last scripted scroll assignment until the actual final row is inside the viewport plus two animation frames. It is not click latency. RAF samples describe this scripted workload, not compositor FPS; uncovered samples during rapid scrolling/jumps remain a visible limitation of four-row overscan over a 150ms RTT. A bounded pipeline cannot remove the network floor. No smoother-scrolling, general fastest-library, or backend CPU/allocation claim follows from reduced request counts.',
        '',
        '## Costs, identity and validation',
        '',
        `Actual full runtime: original ${sizes.before.raw} raw / ${sizes.before.gzip} gzip bytes; one-request ${sizes.after.raw} raw / ${sizes.after.gzip} gzip bytes; two-request ${twoSize.raw} raw / ${twoSize.gzip} gzip bytes. Two-request adds ${twoSize.gzip - sizes.before.gzip} gzip bytes over the original and ${twoSize.gzip - sizes.after.gzip} over the intermediate variant. HTTP/WS compression settings did not change. Raw WS totals count message payloads, excluding framing/TCP/TLS; initial resource observations cover assets, excluding HTML, so no total-wire-byte claim is made.`,
        '',
        '59 Java tests and the dedicated Chrome/Firefox suite cover optional legacy/compact tokens, lazy replacement, keys, partial provider/lifecycle failure, overlap selection, pre-send focus handoff, durable edits, disposal and both old/new client/server combinations. The deterministic suite also exercises repeated identical ranges with distinct tokens, duplicate ACKs, a partial failure alongside another pending request, reconnect and refresh with both slots occupied, and stale initial/generation acknowledgments. Acknowledged range failures retry once; unrelated successful requests cannot reset that retry budget. Final consumer/browser regression results accompany delivery.',
        '',
        'Measured artifacts:'
    );
    for (const version of ['before', 'after', 'two-flight']) lines.push(`- build/virtual-list-${version}.jar: ${inventory[version].sha256}.`);
    lines.push(
        '',
        'source-after.json/runtime-sizes.json describe the preserved one-request measurement snapshot. source-two-flight.json/runtime-two-flight.json describe the two-request candidate. inventory-two-flight.json verifies all three fixture sets. The actual preserved JAR hashes identify measured binaries even if documentation changes later. Raw latency.json records every session, condition, trace and failure status; latency-summary.json contains these cohort summaries.',
        '',
        'The range-only selection evidence, heap limitations, original matrix, upstream pin/license and reproduction instructions remain in [the earlier report](VIRTUAL-LIST.md) and [benchmark README](../../benchmarks/virtual-list/README.md). Chrome-only heap snapshots include document/runtime/instrumentation, have trace arrays cleared before final GC, and are not leak proofs. Firefox heap/unsupported long tasks are unavailable. Variable heights, touch inertia, WAN loss/bandwidth, concurrent users, backend assembly/GC and unrelated JS APIs remain unmeasured. No competitor fixture, compression setting, CI action, commit, push or deployment was changed for this follow-up.'
    );
    writeFileSync(resolve(__dirname, '../../performance-results/virtual-list/latency-summary.json'), JSON.stringify(rows, null, 2) + '\n');
    writeFileSync(resolve(__dirname, '../../performance-results/virtual-list/VIRTUAL-LIST-LATENCY.md'), lines.join('\n') + '\n');
    console.log('Validated 72 visits, 12 warmups and all three fixture identities');
}

main();
