/**
 * The build substitutes exactly one candidate range implementation.
 */
import {calculateVirtualRange} from './fixed-range.js';

/**
 * Exposes deterministic range-only batches; checksum and exact boundary checks prevent dead work.
 * Wall time is subsystem CPU in this browser, not a claim about complete list rendering.
 */
window.measureRange = (count, iterations) => {
    const state = {count, rowHeight: 32, overscan: 4};
    const start = performance.now();
    calculateVirtualRange(state, 0, 320);
    const initializationMs = performance.now() - start;
    let checksum = 0;
    const loopStart = performance.now();
    for (let index = 0; index < iterations; index++) {
        const row = (index * 127) % (count - 10);
        calculateVirtualRange(state, row * 32 + 0.25, 320);
        const first = Math.max(0, row - 4);
        const end = Math.min(count, row + 15);
        if (state.desiredFirst !== first || state.desiredEnd !== end) throw new Error('Range mismatch at ' + row);
        checksum += state.desiredFirst + state.desiredEnd;
    }
    const loopMs = performance.now() - loopStart;
    window.retainedRangeState = state;
    return {initializationMs, loopMs, iterations, checksum};
};

/**
 * Checks changing count, row height, viewport height and overscan on the same caller state.
 * This is correctness evidence only; the timed loop deliberately holds configuration constant.
 */
window.checkRangeConfigurations = () => {
    const state = {count: 10000, rowHeight: 32, overscan: 4};
    let checked = 0;
    for (const count of [10000, 100000, 1, 0, 10000])
        for (const rowHeight of [32, 16.5])
            for (const overscan of [4, 0, 8])
                for (const height of [320, 321, 96.5]) {
                    Object.assign(state, {count, rowHeight, overscan});
                    const top = Math.max(0, count * rowHeight - height - 0.25);
                    calculateVirtualRange(state, top, height);
                    const first = Math.max(0, Math.min(count, Math.floor(top / rowHeight) - overscan));
                    const end = Math.min(count, first + 4096, Math.ceil((top + height) / rowHeight) + overscan);
                    if (state.desiredFirst !== first || state.desiredEnd !== end) throw new Error('Changed configuration range mismatch');
                    checked++;
                }
    return checked;
};
