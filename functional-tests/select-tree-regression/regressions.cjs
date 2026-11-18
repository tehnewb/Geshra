/**
 * Existing project browser engines; this suite installs no dependencies.
 */
const {chromium, firefox} = require('../../browser-tests/node_modules/playwright');
/**
 * Behavioral assertions with nonzero failure exit status.
 */
const assert = require('node:assert/strict');
/**
 * Persists case results and snapshots under the actual new artifact hash.
 */
const fs = require('node:fs');
/**
 * Resolves owned evidence paths.
 */
const path = require('node:path');
/**
 * Independently verifies the artifact hash against launch metadata and the PID file.
 */
const {readBuildIdentity} = require('../build-identity.cjs');

/**
 * Decodes actual outgoing value/silent-sync frames for evidence without replacing either codec.
 * Returns null for unrelated actions; malformed bounded value frames are rejected explicitly.
 */
function decodeValueAction(payload) {
    /*
     * Compact actions use two canonical unsigned integers; legacy values retain fixed header widths.
     */
    const bytes = Buffer.from(payload);
    const compact = bytes[0] === 12;
    const opcode = bytes[compact ? 1 : 0];
    if (opcode !== 4 && opcode !== 10) return null;
    let offset = compact ? 2 : 1; // Next unread byte in this observed frame.
    const numbers = [];
    if (compact) {
        for (let field = 0; field < 2; field++) {
            let value = 0; // Current unsigned ID or UTF-8 byte length.
            let shift = 0; // Base-128 bit position, bounded to signed-32-bit protocol values.
            while (true) {
                assert.ok(offset < bytes.length && shift <= 28, 'Malformed observed compact value frame');
                const next = bytes[offset++];
                value += (next & 127) * 2 ** shift;
                if (!(next & 128)) break;
                shift += 7;
            }
            numbers.push(value);
        }
    } else {
        assert.ok(bytes.length >= 7, 'Truncated observed legacy value frame');
        numbers.push(bytes.readInt32BE(offset), bytes.readUInt16BE(offset + 4));
        offset += 6;
    }
    assert.equal(offset + numbers[1], bytes.length, 'Unexpected observed value payload length');
    return {opcode, id: numbers[0], value: bytes.toString('utf8', offset), bytes: [...bytes]};
}

/**
 * Exercises actual Java fixtures with trusted browser inputs after explicit new-build readiness.
 * EXPECTED_BUILD_SHA is mandatory to prevent accidentally testing the known old shared server.
 */
async function main() {
    /*
     * Keep fresh sessions per case so callback counts, selected state and focus cannot leak.
     */
    assert.ok(process.env.EXPECTED_BUILD_SHA, 'Wait for audited readiness and set EXPECTED_BUILD_SHA before running browsers');
    const identity = readBuildIdentity();
    assert.equal(identity.sha256, process.env.EXPECTED_BUILD_SHA.toUpperCase());
    const engine = process.env.BROWSER || 'chromium';
    const output = path.join(__dirname, 'evidence', identity.sha256, engine);
    fs.mkdirSync(output, {recursive: true});
    const browser = await (engine === 'firefox' ? firefox : chromium).launch({headless: true, ...(engine === 'firefox' ? {} : {channel: 'chrome'})});
    const results = [];
    const diagnostics = [];

    /**
     * Opens a new isolated fixture session, records one outcome, and always closes its context.
     */
    async function check(name, action) {
        const context = await browser.newContext({viewport: {width: 1280, height: 900}});
        const page = await context.newPage();
        const traffic = {values: [], serverDomPackets: 0};
        page.on('websocket', (socket) => {
            socket.on('framesent', (event) => {
                try {
                    const decoded = decodeValueAction(event.payload);
                    if (decoded) traffic.values.push(decoded);
                } catch (error) {
                    diagnostics.push({case: name, kind: 'value-frame-decode', message: error.message});
                }
            });
            socket.on('framereceived', (event) => {
                const opcode = Buffer.from(event.payload)[0];
                if (opcode === 1 || opcode === 7) traffic.serverDomPackets++;
            });
        });
        page.setDefaultTimeout(5000);
        page.on('pageerror', (error) => diagnostics.push({case: name, kind: 'pageerror', message: error.message}));
        page.on('console', (message) => {
            if (message.type() === 'error') diagnostics.push({case: name, kind: 'console', message: message.text()});
        });
        page.on('requestfailed', (request) => diagnostics.push({case: name, kind: 'requestfailed', url: request.url(), error: request.failure()}));
        try {
            await context.route('**/favicon.ico', (route) => route.fulfill({status: 204}));
            await page.goto(new URL('/showcase-regressions', identity.url).href);
            await page.locator('[data-showcase-regressions-ready="true"]').waitFor();
            const observations = await action(page, traffic);
            results.push({name, status: 'PASS', observations});
        } catch (error) {
            const failure = {name, status: 'FAIL', error: error.message};
            try {
                failure.screenshot = path.join(output, name.replace(/[^a-z0-9]+/gi, '-') + '.png');
                await page.screenshot({path: failure.screenshot, fullPage: true});
                failure.dom = await page.locator('body').innerHTML();
            } catch (captureError) {
                failure.evidenceFailure = captureError.message;
            }
            results.push(failure);
        } finally {
            await context.close();
        }
        const latest = results.at(-1);
        console.log(JSON.stringify({name: latest.name, status: latest.status, error: latest.error}));
    }

    /**
     * Checks exact native selection and visible Java getter/callback-count feedback.
     */
    async function readSelect(page, name, value, selectedIndex, callbacks = 0, javaValue = value) {
        const select = page.getByLabel(name, {exact: true});
        await page.waitForFunction(
            ({label, expected, index}) => {
                const element = [...document.querySelectorAll('select')].find((candidate) => candidate.getAttribute('aria-label') === label);
                return element && element.value === expected && element.selectedIndex === index;
            },
            {label: name, expected: value, index: selectedIndex},
            {timeout: 5000}
        );
        await page.getByRole('button', {name: 'Read ' + name, exact: true}).click();
        const feedback = page.locator('[data-regression-feedback="' + name + '"]');
        const expected = 'Java value: ' + javaValue + '; callbacks: ' + callbacks;
        await feedback.filter({hasText: expected}).waitFor();
        assert.equal(await feedback.textContent(), expected);
        return select.evaluate((element) => ({
            value: element.value,
            selectedIndex: element.selectedIndex,
            options: [...element.options].map((option) => ({
                value: option.value,
                selected: option.selected,
                disabled: option.disabled,
                groupDisabled: option.parentElement.tagName === 'OPTGROUP' && option.parentElement.disabled
            }))
        }));
    }

    /**
     * Verifies the server-driven active tree item and the unique visible roving stop.
     */
    async function active(page, name) {
        await page.waitForFunction(
            (expected) => {
                const focused = document.activeElement;
                const tree = document.querySelector('[role="tree"][aria-label="Project files"]');
                const stops = tree ? [...tree.querySelectorAll('[role="treeitem"][tabindex="0"]')].filter((element) => element.getClientRects().length) : [];
                return focused?.getAttribute('role') === 'treeitem' && focused.getAttribute('aria-label') === expected && stops.length === 1 && stops[0] === focused;
            },
            name,
            {timeout: 5000}
        );
        const stops = await page
            .getByRole('tree', {name: 'Project files', exact: true})
            .locator('[role="treeitem"][tabindex="0"]')
            .evaluateAll((elements) => elements.filter((element) => element.getClientRects().length).map((element) => element.getAttribute('aria-label')));
        assert.deepEqual(stops, [name]);
    }

    /**
     * Applies one trusted key and waits for its observable server-directed focus result.
     */
    async function key(page, pressed, expected) {
        await page.keyboard.press(pressed);
        await active(page, expected);
    }

    /**
     * Reads Java selection cardinality and compares it with item ARIA after a user action.
     */
    async function selection(page, names, mode = 'MULTIPLE') {
        const tree = page.getByRole('tree', {name: 'Project files', exact: true});
        await page.getByRole('button', {name: 'Read tree selection', exact: true}).click();
        await page.getByText('Selected nodes: ' + names.length, {exact: true}).waitFor();
        assert.deepEqual(
            (await tree.locator('[role="treeitem"][aria-selected="true"]').evaluateAll((elements) => elements.map((element) => element.getAttribute('aria-label')))).sort(),
            [...names].sort()
        );
        assert.equal(await tree.getAttribute('aria-multiselectable'), String(mode === 'MULTIPLE'));
        if (mode === 'NONE') assert.equal(await tree.locator('[role="treeitem"][aria-selected]').count(), 0);
    }

    /**
     * Uses trusted keyboard navigation to reveal and focus the lifecycle fixture leaf.
     */
    async function focusMain(page) {
        await page.getByRole('treeitem', {name: 'Project', exact: true}).focus();
        await key(page, 'ArrowRight', 'Project');
        await key(page, 'ArrowDown', 'Sources');
        await key(page, 'ArrowRight', 'Sources');
        await key(page, 'ArrowRight', 'Main.java');
    }

    try {
        await check('split normalization followed by delayed INITIAL_VALUE synchronizes real Java silently', async (page, traffic) => {
            const select = page.getByLabel('split-select', {exact: true});
            const number = page.getByLabel('split-number', {exact: true});
            const selectId = Number(await select.getAttribute('id'));
            const numberId = Number(await number.getAttribute('id'));

            /**
             * Reads both actual Java getters only after the previous server mutation has completed.
             */
            async function readPhase(selectValue, numberValue) {
                for (const [name, value] of [
                    ['split-select', selectValue],
                    ['split-number', numberValue]
                ]) {
                    await page.getByRole('button', {name: 'Read ' + name, exact: true}).click();
                    const feedback = page.locator('[data-regression-feedback="' + name + '"]');
                    const expected = 'Java value: ' + value + '; callbacks: 0';
                    await feedback.filter({hasText: expected}).waitFor();
                    assert.equal(await feedback.textContent(), expected);
                }
                return {
                    selectJava: selectValue,
                    numberJava: numberValue,
                    selectDom: await select.inputValue(),
                    numberDom: await number.inputValue(),
                    domPacketCount: traffic.serverDomPackets
                };
            }

            const initial = await readPhase('javascript', '42.0');
            assert.equal(initial.selectDom, 'javascript');
            assert.equal(Number(initial.numberDom), 42);
            const firstValueFrame = traffic.values.length;
            await page.getByRole('button', {name: 'Deliver split normalization', exact: true}).click();
            const normalized = await readPhase('', 'null');
            assert.equal(normalized.selectDom, '');
            assert.equal(normalized.numberDom, '');
            const normalizationFrames = traffic.values.slice(firstValueFrame).filter((frame) => frame.id === selectId || frame.id === numberId);
            for (const id of [selectId, numberId])
                assert.ok(
                    normalizationFrames.some((frame) => frame.opcode === 10 && frame.id === id && frame.value === ''),
                    'Intermediate normalization must cross the real socket for control ' + id
                );
            const finalValueFrame = traffic.values.length;
            await page.getByRole('button', {name: 'Deliver delayed initial values', exact: true}).click();
            const final = await readPhase('javascript', '42.0');
            assert.equal(final.selectDom, 'javascript');
            assert.equal(Number(final.numberDom), 42);
            const finalFrames = traffic.values.slice(finalValueFrame).filter((frame) => frame.id === selectId || frame.id === numberId);
            assert.ok(finalFrames.some((frame) => frame.opcode === 10 && frame.id === selectId && frame.value === 'javascript'));
            assert.ok(finalFrames.some((frame) => frame.opcode === 10 && frame.id === numberId && Number(frame.value) === 42));
            assert.equal(traffic.values.slice(firstValueFrame).filter((frame) => (frame.id === selectId || frame.id === numberId) && frame.opcode === 4).length, 0);
            assert.ok(normalized.domPacketCount > initial.domPacketCount && final.domPacketCount > normalized.domPacketCount);
            return {
                initial,
                normalized,
                final,
                normalizationFrames,
                finalFrames,
                scope: 'Real server DOM batches and Java silent receiver; controlled replay of delayed initialization, not original creation scheduler'
            };
        });
        await check('UI.setTitle without SeoPage', async (page) => {
            await page.waitForFunction(() => document.title === 'Geshra UI regression fixtures', null, {timeout: 5000});
            assert.equal(await page.title(), 'Geshra UI regression fixtures');
        });
        for (const [name, value, index] of [
            ['grouped-default', 'java', 0],
            ['explicit-existing', 'javascript', 1],
            ['explicit-missing', '', -1],
            ['explicit-empty', '', 2],
            ['selected-option', 'javascript', 1],
            ['disabled-first', 'enabled', 1],
            ['disabled-group', 'enabled', 1],
            ['all-disabled', '', -1],
            ['listbox-default', '', -1],
            ['multiple-default', '', -1],
            ['explicit-property', 'javascript', 1],
            ['empty-then-insert', '', -1],
            ['later-insertion', 'java', 0]
        ])
            await check('Select initial ' + name, (page) => readSelect(page, name, value, index));
        for (const name of ['multiple-property', 'multiple-index'])
            await check('Select initial ' + name, async (page) => {
                const observed = await readSelect(page, name, 'javascript', 1, 0, 'vjavascript');
                assert.deepEqual(
                    observed.options.filter((option) => option.selected).map((option) => option.value),
                    ['javascript']
                );
                assert.equal(await page.getByLabel(name, {exact: true}).evaluate((element) => element.multiple), true);
                return observed;
            });
        await check('empty Select populated later through Java', async (page) => {
            await readSelect(page, 'empty-then-insert', '', -1);
            await page.getByRole('button', {name: 'Populate empty select', exact: true}).click();
            return readSelect(page, 'empty-then-insert', 'java', 0);
        });
        await check('silent assignment and later insertion preserve selection', async (page) => {
            await readSelect(page, 'later-insertion', 'java', 0);
            await page.getByRole('button', {name: 'Assign JavaScript silently', exact: true}).click();
            await readSelect(page, 'later-insertion', 'javascript', 1);
            await page.getByRole('button', {name: 'Append another option', exact: true}).click();
            await page.getByLabel('later-insertion', {exact: true}).locator('option[value="later"]').waitFor({state: 'attached'});
            return readSelect(page, 'later-insertion', 'javascript', 1);
        });
        await check('trusted Select keyboard callback and disabled skip', async (page) => {
            const select = page.getByLabel('grouped-default', {exact: true});
            await select.focus();
            await page.keyboard.press('ArrowDown');
            await page.keyboard.press('Tab');
            await readSelect(page, 'grouped-default', 'javascript', 1, 1);
            await page.getByLabel('disabled-first', {exact: true}).focus();
            await page.keyboard.press('Home');
            await page.keyboard.press('Tab');
            return readSelect(page, 'disabled-first', 'enabled', 1);
        });
        await check('Tree ARIA and true Tab entry exit', async (page) => {
            const tree = page.getByRole('tree', {name: 'Project files', exact: true});
            const initialSelection = await tree
                .locator('[role="treeitem"]')
                .evaluateAll((elements) => elements.map((element) => ({name: element.getAttribute('aria-label'), selected: element.getAttribute('aria-selected')})));
            assert.equal(initialSelection.length, 4);
            for (const node of initialSelection) assert.equal(node.selected, 'false', 'Initial selection semantics for ' + node.name);
            assert.equal(await tree.getAttribute('tabindex'), '-1');
            assert.equal(await tree.getByRole('treeitem', {name: 'Project', exact: true}).getAttribute('aria-expanded'), 'false');
            assert.equal(await tree.getByRole('treeitem', {name: 'README.md', exact: true, includeHidden: true}).getAttribute('aria-expanded'), null);
            await page.getByLabel('Tree selection mode').focus();
            await key(page, 'Tab', 'Project');
            await page.keyboard.press('Tab');
            await page.waitForFunction(() => document.activeElement?.textContent === 'Read tree selection');
            await key(page, 'Shift+Tab', 'Project');
            assert.equal(await tree.locator('[role="group"]').count(), 4);
        });
        await check('Tree arrows visible preorder Home End boundaries', async (page) => {
            await page.getByLabel('Tree selection mode').focus();
            await key(page, 'Tab', 'Project');
            await key(page, 'ArrowUp', 'Project');
            await key(page, 'ArrowRight', 'Project');
            await page
                .getByRole('treeitem', {name: 'Project', exact: true})
                .filter({has: page.locator('[role="group"]')})
                .waitFor();
            await key(page, 'ArrowRight', 'Sources');
            await key(page, 'ArrowRight', 'Sources');
            await key(page, 'ArrowRight', 'Main.java');
            await key(page, 'ArrowRight', 'Main.java');
            await key(page, 'ArrowDown', 'README.md');
            await key(page, 'ArrowDown', 'README.md');
            await key(page, 'Home', 'Project');
            await key(page, 'End', 'README.md');
            await key(page, 'ArrowUp', 'Main.java');
            await key(page, 'ArrowLeft', 'Sources');
            await key(page, 'ArrowLeft', 'Sources');
            await page.getByRole('treeitem', {name: 'Main.java', exact: true, includeHidden: true}).waitFor({state: 'hidden'});
            await key(page, 'ArrowDown', 'README.md');
            await key(page, 'Home', 'Project');
            await key(page, 'ArrowLeft', 'Project');
            await key(page, 'End', 'Project');
            await selection(page, []);
        });
        await check('Tree keyboard selection independent focus and mode transitions', async (page) => {
            const root = page.getByRole('treeitem', {name: 'Project', exact: true});
            await root.focus();
            await key(page, 'ArrowRight', 'Project');
            await key(page, 'ArrowDown', 'Sources');
            await selection(page, []);
            await page.getByRole('treeitem', {name: 'Sources', exact: true}).focus();
            await page.keyboard.press('Space');
            await selection(page, ['Sources']);
            await page.getByRole('treeitem', {name: 'README.md', exact: true}).focus();
            await page.keyboard.press('Space');
            await selection(page, ['Sources', 'README.md']);
            await page.getByLabel('Tree selection mode').selectOption('SINGLE');
            await selection(page, ['Sources'], 'SINGLE');
            await page.getByRole('treeitem', {name: 'README.md', exact: true}).focus();
            await page.keyboard.press('Enter');
            await selection(page, ['README.md'], 'SINGLE');
            await page.getByLabel('Tree selection mode').selectOption('NONE');
            await selection(page, [], 'NONE');
            await root.focus();
            await key(page, 'ArrowDown', 'Sources');
            await page.keyboard.press('Space');
            await selection(page, [], 'NONE');
        });
        await check('Tree pointer ctrl shift and rename retain node identity', async (page) => {
            const sample = page.locator('[data-demo="Tree"]');
            await sample.getByText('+', {exact: true}).first().click();
            await sample.getByText('Sources', {exact: true}).waitFor();
            await sample.getByText('+', {exact: true}).first().click();
            await sample.getByText('Main.java', {exact: true}).waitFor();
            await sample.getByText('Sources', {exact: true}).click();
            await sample.getByText('README.md', {exact: true}).click({modifiers: ['Control']});
            await selection(page, ['Sources', 'README.md']);
            await sample.getByText('Project', {exact: true}).click();
            await sample.getByText('README.md', {exact: true}).click({modifiers: ['Shift']});
            await selection(page, ['Project', 'Sources', 'Main.java', 'README.md']);
            const child = await page.getByRole('treeitem', {name: 'Main.java', exact: true}).elementHandle();
            await page.getByRole('button', {name: 'Rename tree branch', exact: true}).click();
            await page.getByRole('treeitem', {name: 'Renamed sources', exact: true}).waitFor();
            assert.equal(await page.getByRole('treeitem', {name: 'Main.java', exact: true}).evaluate((element, original) => element === original, child), true);
            await page.getByRole('treeitem', {name: 'Renamed sources', exact: true}).focus();
            await key(page, 'ArrowLeft', 'Renamed sources');
            await key(page, 'ArrowRight', 'Renamed sources');
            await key(page, 'ArrowRight', 'Main.java');
        });
        await check('long Tree End and Home focus reveal the target label', async (page) => {
            const tree = page.getByRole('tree', {name: 'Long tree', exact: true});
            await tree.getByRole('treeitem', {name: 'Long root', exact: true}).focus();
            for (const [pressed, name] of [
                ['End', 'Row 99'],
                ['Home', 'Long root']
            ]) {
                await page.keyboard.press(pressed);
                await page.waitForFunction((expected) => document.activeElement?.getAttribute('aria-label') === expected, name, {timeout: 5000});
                await page.waitForFunction(
                    (expected) => {
                        const viewport = document.querySelector('[data-regression="long-tree"]');
                        const item = [...viewport.querySelectorAll('[role="treeitem"]')].find((element) => element.getAttribute('aria-label') === expected);
                        const label = item?.querySelector(':scope > .geshra-tree-label');
                        if (!label) return false;
                        const bounds = viewport.getBoundingClientRect();
                        const rect = label.getBoundingClientRect();
                        return rect.top >= bounds.top - 1 && rect.bottom <= bounds.bottom + 1;
                    },
                    name,
                    {timeout: 5000}
                );
                assert.deepEqual(await tree.locator('[role="treeitem"][tabindex="0"]').evaluateAll((elements) => elements.map((element) => element.getAttribute('aria-label'))), [
                    name
                ]);
            }
            return page
                .locator('[data-regression="long-tree"]')
                .evaluate((element) => ({scrollTop: element.scrollTop, scrollHeight: element.scrollHeight, clientHeight: element.clientHeight}));
        });
        await check('Tabbed ARIA relationships and automatic keyboard activation', async (page) => {
            const sample = page.locator('[data-demo="Tabbed"]');
            const tabs = sample.getByRole('tablist', {name: 'Tabs', exact: true});
            const overview = tabs.getByRole('tab', {name: 'Overview', exact: true});
            const settings = tabs.getByRole('tab', {name: 'Settings', exact: true});
            assert.equal(await tabs.getByRole('tab').count(), 2);
            const relationships = await tabs.getByRole('tab').evaluateAll((elements) =>
                elements.map((tab) => {
                    const panel = document.getElementById(tab.getAttribute('aria-controls'));
                    return {tab: tab.textContent, role: panel?.getAttribute('role'), label: panel?.getAttribute('aria-labelledby'), tabId: tab.id};
                })
            );
            for (const relationship of relationships) {
                assert.equal(relationship.role, 'tabpanel');
                assert.equal(relationship.label, relationship.tabId);
            }
            await overview.focus();
            for (const [pressed, name] of [
                ['ArrowRight', 'Settings'],
                ['ArrowRight', 'Overview'],
                ['End', 'Settings'],
                ['Home', 'Overview'],
                ['ArrowLeft', 'Settings']
            ]) {
                await page.keyboard.press(pressed);
                await page.waitForFunction(
                    (expected) =>
                        document.activeElement?.getAttribute('role') === 'tab' &&
                        document.activeElement.textContent === expected &&
                        document.activeElement.getAttribute('aria-selected') === 'true',
                    name,
                    {timeout: 5000}
                );
                assert.deepEqual(await tabs.locator('[role="tab"][tabindex="0"]').allTextContents(), [name]);
                await sample.getByText(name + ' tab content', {exact: true}).waitFor();
            }
            await overview.click();
            await sample.getByText('Overview tab content', {exact: true}).waitFor();
            await settings.focus();
            await page.keyboard.press('Enter');
            await sample.getByText('Settings tab content', {exact: true}).waitFor();
            await overview.focus();
            await page.keyboard.press('Space');
            await sample.getByText('Overview tab content', {exact: true}).waitFor();
            return relationships;
        });
        await check('Tabbed disabled selected all-disabled and enable cycle', async (page) => {
            const sample = page.locator('[data-demo="Tabbed"]');
            const tabs = sample.getByRole('tablist');
            const overview = tabs.getByRole('tab', {name: 'Overview', exact: true});
            const settings = tabs.getByRole('tab', {name: 'Settings', exact: true});
            await sample.getByRole('button', {name: 'Disable selected tab', exact: true}).click();
            await page.waitForFunction(() => {
                const tab = [...document.querySelectorAll('[role="tab"]')].find((element) => element.textContent === 'Overview');
                return tab?.disabled && tab.getAttribute('tabindex') === '-1';
            });
            assert.equal(await overview.getAttribute('aria-disabled'), 'true');
            assert.equal(await overview.getAttribute('aria-selected'), 'true');
            await sample.getByText('Overview tab content', {exact: true}).waitFor();
            assert.deepEqual(await tabs.locator('[role="tab"][tabindex="0"]').allTextContents(), ['Settings']);
            await settings.focus();
            await page.keyboard.press('ArrowLeft');
            await page.waitForFunction(() => document.activeElement?.textContent === 'Settings' && document.activeElement.getAttribute('aria-selected') === 'true');
            await sample.getByText('Settings tab content', {exact: true}).waitFor();
            await sample.getByRole('button', {name: 'Disable all tabs', exact: true}).click();
            await page.waitForFunction(() => [...document.querySelectorAll('[role="tab"]')].every((element) => element.disabled && element.getAttribute('tabindex') === '-1'));
            assert.equal(await tabs.locator('[role="tab"][tabindex="0"]').count(), 0);
            await sample.getByRole('button', {name: 'Enable all tabs', exact: true}).click();
            await page.waitForFunction(() => [...document.querySelectorAll('[role="tab"]')].every((element) => !element.disabled));
            assert.deepEqual(await tabs.locator('[role="tab"][tabindex="0"]').allTextContents(), ['Settings']);
            await overview.click();
            await sample.getByText('Overview tab content', {exact: true}).waitFor();
            await sample.getByRole('button', {name: 'Disable all tabs', exact: true}).click();
            await page.waitForFunction(() => [...document.querySelectorAll('[role="tab"]')].every((element) => element.disabled));
            await sample.getByRole('button', {name: 'Enable all tabs', exact: true}).click();
            await page.waitForFunction(() => [...document.querySelectorAll('[role="tab"]')].every((element) => !element.disabled));
            assert.deepEqual(await tabs.locator('[role="tab"][tabindex="0"]').allTextContents(), ['Overview']);
        });
        await check('Tree detach restore retains identity selection cleanup and navigation', async (page) => {
            await focusMain(page);
            const leaf = page.getByRole('treeitem', {name: 'Main.java', exact: true});
            const original = await leaf.elementHandle();
            const originalId = await leaf.getAttribute('id');
            await page.keyboard.press('Enter');
            await selection(page, ['Main.java']);
            await leaf.focus();
            await active(page, 'Main.java');
            await page.getByRole('button', {name: 'Detach Main.java', exact: true}).click();
            await leaf.waitFor({state: 'detached'});
            assert.equal(
                await page.evaluate(() => document.activeElement?.getAttribute('role') === 'treeitem'),
                false,
                'External mutation action must not steal focus into the tree'
            );
            const sources = page.getByRole('treeitem', {name: 'Sources', exact: true});
            assert.equal(await sources.getAttribute('tabindex'), '0');
            await selection(page, []);
            await sources.focus();
            await key(page, 'ArrowDown', 'README.md');
            await page.getByRole('button', {name: 'Restore Main.java', exact: true}).click();
            await leaf.waitFor();
            assert.equal(await leaf.getAttribute('id'), originalId);
            assert.equal(await leaf.evaluate((element, retained) => element === retained, original), true);
            assert.equal(await page.getByRole('tree', {name: 'Project files', exact: true}).getByRole('treeitem', {name: 'Main.java', exact: true}).count(), 1);
            await sources.focus();
            await key(page, 'ArrowDown', 'Main.java');
            await page.keyboard.press('Enter');
            await selection(page, ['Main.java']);
        });
        await check('Tree disposed leaf and branch skipped by keyboard with clean selection', async (page) => {
            await focusMain(page);
            await page.keyboard.press('Enter');
            await selection(page, ['Main.java']);
            await page.getByRole('treeitem', {name: 'Main.java', exact: true}).focus();
            await page.getByRole('button', {name: 'Dispose Main.java', exact: true}).click();
            await page.getByRole('treeitem', {name: 'Main.java', exact: true}).waitFor({state: 'detached'});
            assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('role') === 'treeitem'), false);
            await selection(page, []);
            const sources = page.getByRole('treeitem', {name: 'Sources', exact: true});
            assert.equal(await sources.getAttribute('aria-expanded'), null);
            await sources.focus();
            await key(page, 'ArrowDown', 'README.md');
            await sources.focus();
            await page.keyboard.press('Enter');
            await selection(page, ['Sources']);
            await sources.focus();
            await page.getByRole('button', {name: 'Dispose Sources branch', exact: true}).click();
            await sources.waitFor({state: 'detached'});
            await selection(page, []);
            const root = page.getByRole('treeitem', {name: 'Project', exact: true});
            await root.focus();
            await key(page, 'ArrowDown', 'README.md');
            await key(page, 'ArrowUp', 'Project');
            assert.equal(await page.getByRole('button', {name: 'Restore Main.java', exact: true}).isDisabled(), true);
        });
        await check('whole Tree disposal releases focused hierarchy and mutation controls', async (page) => {
            await focusMain(page);
            await page.keyboard.press('Enter');
            await selection(page, ['Main.java']);
            await page.getByRole('treeitem', {name: 'Main.java', exact: true}).focus();
            await page.getByRole('button', {name: 'Dispose tree', exact: true}).click();
            await page.getByRole('tree', {name: 'Project files', exact: true}).waitFor({state: 'detached'});
            assert.equal(await page.evaluate(() => document.activeElement?.getAttribute('role') === 'treeitem'), false);
            assert.equal(await page.getByLabel('Tree selection mode').isDisabled(), true);
            assert.equal(await page.getByRole('button', {name: 'Restore Main.java', exact: true}).isDisabled(), true);
            await page.getByRole('button', {name: 'Read tree selection', exact: true}).click();
            await page.getByText('Selected nodes: 0', {exact: true}).waitFor();
        });
        await check('Tree restored sibling retains identity and DOM keyboard order', async (page) => {
            const sample = page.locator('[data-regression="restore-order"]');
            const tree = page.getByRole('tree', {name: 'Sibling restore tree', exact: true});
            const first = tree.getByRole('treeitem', {name: 'A', exact: true});
            const original = await first.elementHandle();
            const originalId = await first.getAttribute('id');
            await sample.getByRole('button', {name: 'Detach A', exact: true}).click();
            await first.waitFor({state: 'detached'});
            await sample.getByRole('button', {name: 'Restore A', exact: true}).click();
            await first.waitFor();
            assert.equal(await first.getAttribute('id'), originalId);
            assert.equal(await first.evaluate((element, retained) => element === retained, original), true);
            assert.deepEqual(await tree.getByRole('treeitem').evaluateAll((elements) => elements.map((element) => element.getAttribute('aria-label'))), [
                'Sibling root',
                'A',
                'B',
                'C'
            ]);
            await tree.getByRole('treeitem', {name: 'Sibling root', exact: true}).focus();
            for (const name of ['A', 'B', 'C']) {
                await page.keyboard.press('ArrowDown');
                await page.waitForFunction((expected) => document.activeElement?.getAttribute('aria-label') === expected, name, {timeout: 5000});
            }
        });
        await check('empty Tree remains named keyboard stop', async (page) => {
            const tree = page.getByRole('tree', {name: 'Empty tree', exact: true});
            assert.equal(await tree.getAttribute('tabindex'), '0');
            assert.equal(await tree.getByRole('treeitem').count(), 0);
            await tree.focus();
            await page.keyboard.press('ArrowDown');
            assert.equal(await tree.evaluate((element) => element === document.activeElement), true);
        });
        await check('Tree pointer collapse restores visible focus from descendant', async (page) => {
            const sample = page.locator('[data-demo="Tree"]');
            await sample.getByText('+', {exact: true}).first().click();
            await sample.getByText('Sources', {exact: true}).waitFor();
            await sample.getByText('+', {exact: true}).first().click();
            const child = page.getByRole('treeitem', {name: 'Main.java', exact: true});
            await child.waitFor();
            await child.focus();
            await active(page, 'Main.java');
            const branch = page.getByRole('treeitem', {name: 'Sources', exact: true});
            await branch.locator(':scope > .geshra-tree-label').getByText('-', {exact: true}).click();
            await active(page, 'Sources');
            await child.waitFor({state: 'hidden'});
            assert.equal(await branch.getAttribute('aria-expanded'), 'false');
        });
    } finally {
        try {
            fs.writeFileSync(path.join(output, 'results.json'), JSON.stringify({identity, engine, browser: browser.version(), results, diagnostics}, null, 2));
        } finally {
            await browser.close();
        }
        if (results.some((result) => result.status === 'FAIL') || diagnostics.length) process.exitCode = 1;
    }
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
