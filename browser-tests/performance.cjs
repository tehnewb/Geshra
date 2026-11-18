const {chromium} = require('playwright');
const {writeFileSync, mkdirSync} = require('node:fs');
const {resolve} = require('node:path');
const os = require('node:os');
const {gzipSync} = require('node:zlib');

/**
 * Measures real route traffic, sequential callback latency, and DOM decode/apply cost.
 * Run against the packaged example with BASE_URL and optional BROWSER_EXECUTABLE.
 * Warmups precede timed samples; timing is informational, payload counts are deterministic.
 */
async function benchmark() {
    const browser = await chromium.launch({headless: true, executablePath: process.env.BROWSER_EXECUTABLE || undefined});
    const page = await browser.newPage();
    const incoming = [];
    const outgoing = [];
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    page.on('websocket', (socket) => {
        socket.on('framereceived', (event) => incoming.push(Buffer.byteLength(event.payload)));
        socket.on('framesent', (event) => outgoing.push(Buffer.byteLength(event.payload)));
    });
    await page.route('**/favicon.ico', (route) => route.fulfill({status: 204}));
    try {
        const base = process.env.BASE_URL || 'http://localhost:4040';
        const runtime = await (await page.request.get(base + '/js/runtime.js')).body();
        await page.goto(base + '/components' + (process.env.BENCHMARK_LEGACY ? '?legacy-benchmark' : ''));
        await page.getByRole('heading', {name: 'Component gallery', exact: true}).waitFor();
        await page.waitForFunction(() => document.querySelectorAll('.geshra-virtual-row').length === 12);
        await page.waitForTimeout(100);
        const gallery = {serverBytes: sum(incoming), clientBytes: sum(outgoing), serverFrames: incoming.length, clientFrames: outgoing.length};
        const serverBudget = process.env.BENCHMARK_LEGACY ? 11000 : 9000;
        const clientBudget = process.env.BENCHMARK_LEGACY ? 100 : 60;
        if (gallery.serverBytes > serverBudget || gallery.clientBytes > clientBudget) throw new Error('Gallery traffic exceeded its deterministic regression budget');
        const packets = {incoming: incoming.length, outgoing: outgoing.length};
        const samples = [];
        for (let index = 0; index < 60; index++) {
            const elapsed = await page.evaluate(async () => {
                const button = Array.from(document.querySelectorAll('button')).find((element) => element.textContent === 'Read Java values');
                const output = document.querySelector('[aria-label="Java values"]');
                return new Promise((resolve) => {
                    const observer = new MutationObserver(() => {
                        observer.disconnect();
                        resolve(performance.now() - start);
                    });
                    observer.observe(output, {childList: true, characterData: true, subtree: true});
                    const start = performance.now();
                    button.click();
                });
            });
            if (index >= 10) samples.push(elapsed);
        }
        const callbacks = {
            samples: samples.length,
            medianMs: percentile(samples, 0.5),
            p95Ms: percentile(samples, 0.95),
            serverBytes: sum(incoming.slice(packets.incoming)),
            clientBytes: sum(outgoing.slice(packets.outgoing))
        };
        const dom = await page.evaluate(async () => {
            const encoder = new TextEncoder();
            const value = encoder.encode('benchmark');
            const target = document.createElement('div');
            target.id = '2000000';
            document.body.appendChild(target);
            const count = 1000;
            const bytes = new Uint8Array(3 + count * (9 + value.length));
            const view = new DataView(bytes.buffer);
            bytes[0] = 1;
            view.setUint16(1, count);
            let offset = 3;
            for (let index = 0; index < count; index++) {
                bytes[offset++] = 1;
                view.setInt32(offset, 2000000);
                offset += 4;
                bytes[offset++] = 1;
                bytes[offset++] = 0;
                view.setUint16(offset, value.length);
                offset += 2;
                bytes.set(value, offset);
                offset += value.length;
            }
            const results = {};
            const compact = new Uint8Array(3 + count * (7 + value.length));
            const compactView = new DataView(compact.buffer);
            compact[0] = 7;
            compactView.setUint16(1, count);
            offset = 3;
            for (let index = 0; index < count; index++) {
                compact[offset++] = 1;
                compact[offset++] = 128;
                compact[offset++] = 137;
                compact[offset++] = 122;
                compact[offset++] = 1;
                compact[offset++] = 0;
                compact[offset++] = value.length;
                compact.set(value, offset);
                offset += value.length;
            }
            const formats = location.search.includes('legacy-benchmark')
                ? [['legacy', bytes]]
                : [
                      ['legacy', bytes],
                      ['compact', compact]
                  ];
            for (const [format, packet] of formats) {
                const samples = [];
                for (let index = 0; index < 120; index++) {
                    const start = performance.now();
                    window.domSocket.onmessage({data: packet.buffer});
                    if (index >= 20) samples.push(performance.now() - start);
                }
                samples.sort((left, right) => left - right);
                results[format] = {mutationsPerSample: count, samples: samples.length, payloadBytes: packet.length, medianMs: samples[50], p95Ms: samples[95]};
            }
            if (target.textContent !== 'benchmark') throw new Error('Measured DOM commands were not applied');
            target.remove();
            return results;
        });
        const encoding = process.env.BENCHMARK_LEGACY
            ? null
            : await page.evaluate(async () => {
                  const {encodePacket, setCompactEncoding} = await import('/js/runtime/encode.js');
                  const results = [];
                  const workloads = [
                      ['click', 1, [42, 0, 0, 0, 0, 0, 0, 0, false, false, false, false]],
                      ['unicodeInput', 4, [42, 'Café 東京 😀']],
                      ['viewport', 8, [42, 1, 900000, 900016]],
                      ['largeAscii', 4, [42, 'x'.repeat(60000)]]
                  ];
                  for (const [name, opcode, args] of workloads) {
                      for (const compact of [false, true]) {
                          setCompactEncoding(compact);
                          const iterations = name === 'largeAscii' ? 100 : 10000;
                          let checksum = 0;
                          for (let index = 0; index < iterations; index++) {
                              const packet = encodePacket(opcode, args);
                              window.benchmarkPacket = packet;
                              checksum += packet.length + packet[0] + packet[packet.length - 1];
                          }
                          const samples = [];
                          for (let sample = 0; sample < 20; sample++) {
                              const start = performance.now();
                              for (let index = 0; index < iterations; index++) {
                                  const packet = encodePacket(opcode, args);
                                  window.benchmarkPacket = packet;
                                  checksum += packet.length + packet[0] + packet[packet.length - 1];
                              }
                              samples.push(((performance.now() - start) * 1000000) / iterations);
                          }
                          const packet = encodePacket(opcode, args);
                          if (compact && packet.buffer.byteLength !== packet.byteLength) throw new Error('Compact encoder retained oversized storage');
                          samples.sort((left, right) => left - right);
                          results.push({
                              name,
                              compact,
                              iterations,
                              medianNs: samples[10],
                              p95Ns: samples[19],
                              payloadBytes: packet.byteLength,
                              retainedBytes: packet.buffer.byteLength,
                              checksum
                          });
                      }
                  }
                  delete window.benchmarkPacket;
                  return results;
              });
        if (errors.length) throw new Error(errors.join('\n'));
        const result = {
            revision: process.env.BENCHMARK_LABEL || 'working-tree',
            recordedAt: new Date().toISOString(),
            environment: {node: process.version, browser: browser.version(), platform: os.platform(), arch: os.arch(), cpu: os.cpus()[0].model},
            gallery,
            callbacks,
            runtime: {bytes: runtime.length, gzipBytes: gzipSync(runtime).length},
            dom,
            encoding
        };
        console.log(JSON.stringify(result, null, 2));
        if (process.env.BENCHMARK_OUTPUT) {
            const file = resolve(process.env.BENCHMARK_OUTPUT);
            mkdirSync(resolve(file, '..'), {recursive: true});
            writeFileSync(file, JSON.stringify(result, null, 2) + '\n');
        }
    } finally {
        await browser.close();
    }
}

/**
 * Returns total application payload bytes, excluding WebSocket/TCP/TLS overhead.
 */
function sum(values) {
    return values.reduce((total, value) => total + value, 0);
}

/**
 * Returns an order statistic from a copied sample array.
 */
function percentile(values, fraction) {
    const sorted = values.slice().sort((left, right) => left - right);
    return sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * fraction))];
}

benchmark().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
