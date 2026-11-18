const {readFileSync} = require('node:fs');
const {resolve} = require('node:path');

/**
 * Compares browser payload and timing evidence without treating unlike machines as a speedup.
 * Pass baseline and candidate JSON paths, or use the checked-in same-machine measurements.
 * Negative percentages mean less traffic or elapsed time; shared-runner timing is advisory.
 */
function comparePerformance() {
    const baseline = JSON.parse(readFileSync(resolve(process.argv[2] || '../performance-results/browser-before.json'), 'utf8'));
    const candidate = JSON.parse(readFileSync(resolve(process.argv[3] || '../performance-results/browser-after.json'), 'utf8'));
    for (const field of ['browser', 'platform', 'arch', 'cpu']) {
        if (baseline.environment[field] !== candidate.environment[field]) throw new Error('Environment mismatch: ' + field);
    }
    const comparisons = [
        ['Gallery server bytes', baseline.gallery.serverBytes, candidate.gallery.serverBytes],
        ['Gallery client bytes', baseline.gallery.clientBytes, candidate.gallery.clientBytes],
        ['Callback client bytes', baseline.callbacks.clientBytes, candidate.callbacks.clientBytes],
        ['Callback server bytes', baseline.callbacks.serverBytes, candidate.callbacks.serverBytes],
        ['Runtime bytes', baseline.runtime.bytes, candidate.runtime.bytes],
        ['Runtime gzip bytes', baseline.runtime.gzipBytes, candidate.runtime.gzipBytes],
        ['Callback median ms', baseline.callbacks.medianMs, candidate.callbacks.medianMs],
        ['DOM median ms', baseline.dom.legacy.medianMs, candidate.dom.compact.medianMs]
    ];
    console.table(comparisons.map(([metric, before, after]) => ({metric, before, after, changePercent: Number(((after / before - 1) * 100).toFixed(2))})));
}

comparePerformance();
