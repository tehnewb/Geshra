/**
 * Existing pinned tools; neither is added to the published Java/browser runtime.
 */
const {chromium, firefox} = require('../../browser-tests/node_modules/playwright');
/**
 * Reproducible production minifier used by the library itself.
 */
const {buildSync} = require('../../browser-tests/node_modules/esbuild');
/**
 * Bundle/artifact persistence and actual input hashing.
 */
const {writeFileSync, readFileSync} = require('node:fs');
/**
 * Absolute repository-local paths.
 */
const {resolve} = require('node:path');
/**
 * Actual compressed bundle size, not package-directory size.
 */
const {gzipSync} = require('node:zlib');
/**
 * Byte-level provenance.
 */
const {createHash} = require('node:crypto');

/**
 * Builds both isolated subsystem candidates with identical minification and measurement code.
 */
function bundles() {
    return ['fixed', 'tanstack'].map((name) => {
        const entry = readFileSync(resolve(__dirname, 'range-entry.js'), 'utf8').replace('./fixed-range.js', './' + name + '-range.js');
        const result = buildSync({
            stdin: {contents: entry, resolveDir: __dirname},
            bundle: true,
            minify: true,
            format: 'iife',
            target: ['es2020'],
            legalComments: 'eof',
            write: false
        });
        const code = result.outputFiles[0].text;
        writeFileSync(resolve(__dirname, name + '-range.bundle.js'), code);
        return {name, code, bytes: Buffer.byteLength(code), gzipBytes: gzipSync(code).length, sha256: createHash('sha256').update(code).digest('hex')};
    });
}

/**
 * Alternates candidate order across fresh contexts, with untimed warmup batches separated.
 * CDP post-GC heap is Chrome-only and includes the blank document and retained range state.
 */
async function main() {
    const candidates = bundles();
    if (process.argv.includes('--build-only')) return;
    const output = {
        recordedAt: new Date().toISOString(),
        package: '@tanstack/virtual-core@3.19.11',
        kind: 'range-subsystem-only',
        candidates: candidates.map(({code, ...metadata}) => metadata),
        samples: []
    };
    for (const [engineName, engine] of [
        ['chrome', chromium],
        ['firefox', firefox]
    ]) {
        const browser = await engine.launch({headless: true, executablePath: engineName === 'chrome' ? 'C:/Program Files/Google/Chrome/Application/chrome.exe' : undefined});
        try {
            for (const count of [10000, 100000])
                for (let repeat = 0; repeat < 6; repeat++)
                    for (const candidate of repeat % 2 ? [...candidates].reverse() : candidates) {
                        const context = await browser.newContext();
                        const page = await context.newPage();
                        try {
                            await page.addScriptTag({content: candidate.code});
                            const cdp = engineName === 'chrome' ? await context.newCDPSession(page) : null;
                            await page.evaluate(() => {
                                window.measureRange(100, 100);
                                window.retainedRangeState = null;
                            });
                            if (cdp) await cdp.send('HeapProfiler.collectGarbage');
                            const before = cdp ? await cdp.send('Runtime.getHeapUsage') : null;
                            const cold = await page.evaluate((count) => window.measureRange(count, 1), count);
                            if (cdp) await cdp.send('HeapProfiler.collectGarbage');
                            const after = cdp ? await cdp.send('Runtime.getHeapUsage') : null;
                            await page.evaluate((count) => window.measureRange(count, 1000), count);
                            const measured = await page.evaluate((count) => window.measureRange(count, 200000), count);
                            const configurationChecks = await page.evaluate(() => window.checkRangeConfigurations());
                            output.samples.push({
                                engine: engineName,
                                browser: browser.version(),
                                count,
                                repeat,
                                candidate: candidate.name,
                                cold,
                                measured,
                                configurationChecks,
                                heapBefore: before,
                                heapAfter: after,
                                retainedHeapDelta: before ? after.usedSize - before.usedSize : null,
                                retainedBackingDelta: before ? after.backingStorageSize - before.backingStorageSize : null
                            });
                        } finally {
                            await context.close();
                        }
                    }
        } finally {
            await browser.close();
        }
    }
    writeFileSync(resolve(__dirname, '../../performance-results/virtual-list/range.json'), JSON.stringify(output, null, 2) + '\n');
    console.log('Recorded ' + output.samples.length + ' isolated range samples');
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
