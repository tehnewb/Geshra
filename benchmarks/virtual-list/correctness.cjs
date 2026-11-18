/**
 * Existing development-only native browsers.
 */
const {chromium, firefox} = require('../../browser-tests/node_modules/playwright');
/**
 * Bundles the actual production modules, without replacement range/transport implementations.
 */
const {buildSync} = require('../../browser-tests/node_modules/esbuild');
/**
 * Absolute entry-point paths.
 */
const {resolve} = require('node:path');
/**
 * Independent host assertions.
 */
const assert = require('node:assert/strict');
/**
 * Retains correctness observations separately from timing samples.
 */
const {writeFileSync, readFileSync} = require('node:fs');

/**
 * Exercises reordered initial acknowledgments, one pending request, bounded failure retries,
 * focus handoff/inert outgoing rows, reconnect resynchronization and explicit disposal.
 */
async function deterministic(browser) {
    const page = await browser.newPage();
    try {
        await page.setContent(
            '<div id="1" tabindex="0" class="geshra-virtual-list" style="height:320px;overflow:auto;position:relative"><div id="2"></div><div id="3" style="position:absolute;top:0"><div class="geshra-virtual-row" aria-posinset="1"><input value="edit"></div></div></div>'
        );
        const code = buildSync({entryPoints: [resolve(__dirname, 'unit-entry.js')], bundle: true, minify: true, format: 'iife', write: false}).outputFiles[0].text;
        await page.addScriptTag({content: code});
        const result = await page.evaluate(async () => {
            const api = window.virtualTest;
            const list = document.getElementById('1');
            document.body.id = '0';
            const packets = [];
            const failures = [];
            window.domSocket = {
                readyState: WebSocket.OPEN,
                send: (bytes) => {
                    packets.push([
                        new DataView(bytes.buffer).getInt32(9),
                        new DataView(bytes.buffer).getInt32(13),
                        bytes.byteLength >= 21 ? new DataView(bytes.buffer).getInt32(17) : 0
                    ]);
                }
            };
            list.addEventListener('geshra-virtual-error', (event) => failures.push(event.detail));
            const configuration = {count: 100000, rowHeight: 32, overscan: 4, revision: 1, spacer: 2, layer: 3, first: 0, end: 14, requestAware: true};
            api.configureVirtualList(list, configuration);
            const state = api.virtualLists.get(list);
            const frame = () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
            list.querySelector('input').focus();
            await frame();
            list.scrollTop = 3200;
            await frame();
            if (packets.length) throw new Error('Sent before initial acknowledgment');
            api.acknowledgeVirtualWindow(state, {revision: 1, first: 0, end: 14, requested: false});
            await frame();
            if (packets.length !== 1 || document.activeElement !== list || !list.querySelector('.geshra-virtual-row').inert)
                throw new Error(
                    'Missing early focus handoff or inert eviction: ' +
                        JSON.stringify({packets, active: document.activeElement.outerHTML, inert: list.querySelector('.geshra-virtual-row').inert, top: list.scrollTop})
                );
            list.scrollTop = 6400;
            api.acknowledgeVirtualWindow(state, {revision: 1, first: packets[0][0], end: packets[0][1], requested: false});
            await frame();
            if (packets.length !== 1 || !state.pending.length) throw new Error('Unrelated acknowledgment released request');
            for (let index = 0; index < 5; index++) {
                list.scrollTop = 6400 + index * 32;
                await frame();
            }
            if (packets.length !== 1) throw new Error('Unbounded outstanding requests');
            const first = packets[0];
            api.acknowledgeVirtualWindow(state, {revision: 1, first: first[0], end: first[1], requestFirst: first[0], requestEnd: first[1], requested: true, token: first[2]});
            await frame();
            if (packets.length !== 2) throw new Error('Latest desired window not sent');
            const second = packets[1];
            api.acknowledgeVirtualWindow(state, {
                revision: 1,
                first: second[0],
                end: second[0] + 2,
                requestFirst: second[0],
                requestEnd: second[1],
                failed: true,
                requested: true,
                token: second[2]
            });
            await frame();
            if (packets.length !== 3) throw new Error('No bounded retry after partial failure');
            api.acknowledgeVirtualWindow(state, {
                revision: 1,
                first: second[0],
                end: second[0] + 2,
                requestFirst: second[0],
                requestEnd: second[1],
                failed: true,
                requested: true,
                token: packets[2][2]
            });
            await frame();
            if (packets.length !== 3 || state.pending.length || failures.length !== 2) throw new Error('Repeated failures must stop retrying');
            list.scrollTop = 9600;
            await frame();
            if (packets.length !== 4) throw new Error('New range cannot recover');
            api.resumeVirtualLists();
            await frame();
            if (packets.length !== 5) throw new Error('Reconnect did not resynchronize latest range');
            const stale = packets[3];
            api.acknowledgeVirtualWindow(state, {revision: 1, first: stale[0], end: stale[1], requestFirst: stale[0], requestEnd: stale[1], requested: true, token: stale[2]});
            await frame();
            if (!state.pending.length || packets.length !== 5) throw new Error('Old same-range acknowledgment released a resubmitted request');
            list.scrollTop = 0;
            api.configureVirtualList(list, {...configuration, revision: 2});
            await frame();
            api.acknowledgeVirtualWindow(state, {revision: 2, first: 0, end: 2, requestFirst: 0, requestEnd: 14, requested: false, failed: true});
            await frame();
            if (packets.length !== 6) throw new Error('Initial failure did not request bounded retry');
            api.acknowledgeVirtualWindow(state, {revision: 2, first: 0, end: 2, requestFirst: 0, requestEnd: 14, requested: true, failed: true, token: packets[5][2]});
            await frame();
            if (packets.length !== 6 || state.pending.length) throw new Error('Initial failure exceeded retry budget');
            api.configureVirtualList(list, {...configuration, revision: 3});
            api.updateDOM(29, 1, []);
            api.updateDOM(30, 0, {16: 1});
            await frame();
            if (packets.length !== 7 || state.awaitingInitialWindow) throw new Error('Retained resynchronization blocked on missing initial acknowledgment');
            api.configureVirtualList(list, {...configuration, revision: 4, maxPending: 2});
            list.scrollTop = 0;
            api.acknowledgeVirtualWindow(state, {revision: 4, first: 0, end: 14, requested: false});
            await frame();
            list.fsiScrollToIndex(100);
            await frame();
            list.fsiScrollToIndex(200);
            await frame();
            list.fsiScrollToIndex(300);
            await frame();
            if (packets.length !== 9 || state.pending.length !== 2) throw new Error('Two-request pipeline exceeded its bound');
            const older = packets[7];
            list.querySelector('.geshra-virtual-row').setAttribute('aria-posinset', '101');
            api.acknowledgeVirtualWindow(state, {revision: 4, first: older[0], end: older[1], requestFirst: older[0], requestEnd: older[1], requested: true, token: older[2]});
            if (!list.querySelector('.geshra-virtual-row').inert) throw new Error('Older acknowledgment enabled a row evicted by a later request');
            await frame();
            if (packets.length !== 10 || state.pending.length !== 2) throw new Error('Freed slot did not send the latest desired range');
            api.acknowledgeVirtualWindow(state, {revision: 4, first: older[0], end: older[1], requestFirst: older[0], requestEnd: older[1], requested: true, token: older[2]});
            if (state.pending.length !== 2) throw new Error('Duplicate acknowledgment freed another slot');
            list.fsiScrollToIndex(200);
            await frame();
            const failedOlder = packets[8];
            api.acknowledgeVirtualWindow(state, {
                revision: 4,
                first: failedOlder[0],
                end: failedOlder[0] + 2,
                requestFirst: failedOlder[0],
                requestEnd: failedOlder[1],
                requested: true,
                failed: true,
                token: failedOlder[2]
            });
            if (state.pending.length !== 1 || state.pending[0].token !== packets[9][2]) throw new Error('Partial failure released a different pending request');
            await frame();
            if (packets.length !== 11 || state.pending.length !== 2) throw new Error('Changed desired range failed to recover alongside pending request');
            const repeated = packets[10];
            api.acknowledgeVirtualWindow(state, {
                revision: 4,
                first: failedOlder[0],
                end: failedOlder[1],
                requestFirst: failedOlder[0],
                requestEnd: failedOlder[1],
                requested: true,
                token: failedOlder[2]
            });
            if (state.pending.length !== 2 || !state.pending.some((request) => request.token === repeated[2]))
                throw new Error('Identical range with a stale token released a newer slot');
            api.resumeVirtualLists();
            await frame();
            if (packets.length !== 12 || state.pending.length !== 1) throw new Error('Reconnect did not clear both pending slots');
            api.acknowledgeVirtualWindow(state, {
                revision: 4,
                first: repeated[0],
                end: repeated[1],
                requestFirst: repeated[0],
                requestEnd: repeated[1],
                requested: true,
                token: repeated[2]
            });
            if (state.pending.length !== 1) throw new Error('Reconnect accepted stale same-range acknowledgment');
            list.fsiScrollToIndex(300);
            await frame();
            if (state.pending.length !== 2 || packets.length !== 13) throw new Error('Second slot not available after reconnect');
            api.configureVirtualList(list, {...configuration, revision: 5, maxPending: 2});
            if (state.pending.length) throw new Error('Refresh retained old pending slots');
            api.acknowledgeVirtualWindow(state, {
                revision: 4,
                first: repeated[0],
                end: repeated[1],
                requestFirst: repeated[0],
                requestEnd: repeated[1],
                requested: true,
                token: repeated[2]
            });
            if (!state.awaitingInitialWindow) throw new Error('Old generation released refresh wait');
            api.acknowledgeVirtualWindow(state, {revision: 5, first: 0, end: 14, requested: false});
            await frame();
            if (state.pending.length !== 1 || packets.length !== 14) throw new Error('Refresh did not resume latest desired range');
            list.scrollTop = 0;
            api.configureVirtualList(list, {...configuration, revision: 6, maxPending: 32});
            api.acknowledgeVirtualWindow(state, {revision: 6, first: 0, end: 14, requested: false});
            await frame();
            for (let step = 0; step < 34; step++) {
                list.scrollTop = (100 + step * 10) * 32;
                await frame();
                if (state.pending.length > 32) throw new Error('Pipeline exceeded its hard cap');
            }
            if (state.pending.length !== 32 || packets.length !== 46) throw new Error('Hard-cap saturation did not retain exactly 32 requests');
            const completing = state.pending[0];
            api.acknowledgeVirtualWindow(state, {
                revision: 6,
                first: completing.first,
                end: completing.end,
                requestFirst: completing.first,
                requestEnd: completing.end,
                requested: true,
                token: completing.token
            });
            await frame();
            if (state.pending.length !== 32 || packets.length !== 47 || packets[46][0] !== 426) throw new Error('Freed hard-cap slot did not send the latest desired range');
            api.configureVirtualList(list, {...configuration, revision: 7, maxPending: 32});
            if (state.pending.length || !state.awaitingInitialWindow) throw new Error('Refresh retained saturated hard-cap slots');
            api.acknowledgeVirtualWindow(state, {
                revision: 6,
                first: completing.first,
                end: completing.end,
                requestFirst: completing.first,
                requestEnd: completing.end,
                requested: true,
                token: completing.token
            });
            if (!state.awaitingInitialWindow) throw new Error('Stale hard-cap generation released refresh');
            api.acknowledgeVirtualWindow(state, {revision: 7, first: 0, end: 14, requested: false});
            await frame();
            for (let step = 0; step < 31; step++) {
                list.scrollTop = (500 + step * 10) * 32;
                await frame();
            }
            if (state.pending.length !== 32 || packets.length !== 79) throw new Error('Refreshed hard-cap pipeline did not saturate');
            api.resumeVirtualLists();
            await frame();
            if (state.pending.length !== 1 || packets.length !== 80) throw new Error('Recovery retained stale hard-cap slots');
            api.cleanupRuntimeTree(list);
            list.scrollTop = 12000;
            list.dispatchEvent(new Event('scroll'));
            await frame();
            if (packets.length !== 80 || api.virtualLists.has(list) || state.pending.length) throw new Error('Disposed observer/listener/request records still active');
            return {packets: packets.length, failureEvents: failures.length, passed: true};
        });
        assert.equal(result.passed, true);
        return result;
    } finally {
        await page.close();
    }
}

/**
 * Verifies native focus/selection, durable edits and short reconnect using the actual packaged app.
 */
async function realServer(browser) {
    const context = await browser.newContext();
    const page = await context.newPage();
    try {
        await page.goto(process.env.VIRTUAL_TEST_URL || 'http://localhost:4402/');
        const list = page.getByLabel('Virtual benchmark', {exact: true});
        const input = page.getByLabel('Edit 5', {exact: true});
        await input.fill('committed edit');
        await input.evaluate((element) => {
            window.originalInput = element;
            element.setSelectionRange(2, 5);
        });
        await list.evaluate((element) => {
            element.scrollTop = 5 * 32;
        });
        await page.waitForTimeout(150);
        assert.equal(await input.evaluate((element) => element === window.originalInput && element.selectionStart === 2 && element.selectionEnd === 5), true);
        await list.evaluate((element) => {
            element.scrollTop = 500 * 32;
        });
        await page.waitForFunction(() => document.activeElement === document.querySelector('.geshra-virtual-list'));
        await page.getByLabel('Edit 505', {exact: true}).waitFor();
        await page.evaluate(() => window.domSocket.close());
        await list.evaluate((element) => {
            element.scrollTop = 1000 * 32;
        });
        await page.getByLabel('Edit 1005', {exact: true}).waitFor();
        await page.getByRole('button', {name: 'First', exact: true}).click();
        await input.waitFor();
        assert.equal(await input.inputValue(), 'committed edit');
        assert.equal(await list.locator('[aria-posinset="6"]').getAttribute('data-geshra-key'), '5');
        await page.getByRole('button', {name: 'Dispose', exact: true}).click();
        await list.waitFor({state: 'detached'});
        return {passed: true, overlapSelection: true, focusHandoff: true, reconnect: true, durableEdit: true, disposal: true};
    } finally {
        await context.close();
    }
}

/**
 * Runs both mixed-deployment combinations against real old/new packaged server libraries.
 * Only the runtime response is replaced; origin/session/WebSocket handling remains genuine.
 */
async function compatibility(browser, oldClient) {
    const context = await browser.newContext();
    const page = await context.newPage();
    const code = readFileSync(resolve(__dirname, oldClient ? '../../performance-results/virtual-list/runtime-before.js' : '../../src/main/resources/web/js/runtime.js'), 'utf8');
    try {
        await page.route('**/js/runtime.js', (route) => route.fulfill({status: 200, contentType: 'application/javascript', body: code}));
        await page.goto(oldClient ? process.env.VIRTUAL_TEST_URL || 'http://localhost:4402/' : 'http://localhost:4302/');
        await page.getByLabel('Edit 0', {exact: true}).waitFor();
        await page.getByRole('button', {name: 'Last', exact: true}).click();
        await page.getByLabel('Edit 99999', {exact: true}).waitFor();
        await page.getByLabel('Edit 99999', {exact: true}).evaluate((element) => {
            window.preRefreshInput = element;
        });
        await page.getByRole('button', {name: 'Refresh', exact: true}).click();
        await page.waitForFunction(() => !window.preRefreshInput.isConnected);
        await page.getByLabel('Edit 99999', {exact: true}).waitFor();
        await page.evaluate(() => {
            window.preReconnectSocket = window.domSocket;
            window.domSocket.close();
        });
        await page.waitForFunction(() => window.domSocket !== window.preReconnectSocket && window.domSocket.readyState === WebSocket.OPEN);
        await page.waitForTimeout(200);
        await page.getByRole('button', {name: 'First', exact: true}).click();
        await page.getByLabel('Edit 0', {exact: true}).waitFor();
        return {oldClient, oldServer: !oldClient, passed: true};
    } finally {
        await context.close();
    }
}

/**
 * Runs the same correctness contracts in both browser engines, independently of timed sessions.
 */
async function main() {
    const results = [];
    for (const [name, launcher] of [
        ['chrome', chromium],
        ['firefox', firefox]
    ]) {
        const browser = await launcher.launch({headless: true, executablePath: name === 'chrome' ? 'C:/Program Files/Google/Chrome/Application/chrome.exe' : undefined});
        try {
            const result = {
                browser: name,
                deterministic: await deterministic(browser),
                real: await realServer(browser),
                compatibility: [await compatibility(browser, true), await compatibility(browser, false)]
            };
            results.push(result);
            console.log(JSON.stringify(result));
        } finally {
            await browser.close();
        }
    }
    writeFileSync(resolve(__dirname, '../../performance-results/virtual-list/correctness.json'), JSON.stringify(results, null, 2) + '\n');
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
