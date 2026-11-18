/**
 * Pinned upstream core under test; its own MIT notice is retained in the bundled candidate.
 */
import {Virtualizer} from '@tanstack/virtual-core';
/**
 * One core instance per caller-owned list state; weak ownership permits normal disposal.
 */
const calculators = new WeakMap();

/**
 * Evaluates the range subsystem only: identical supplied geometry, no duplicated DOM observers.
 * The common Geshra adapter owns scrolling, transport and DOM application for both candidates.
 */
export function calculateVirtualRange(state, top, height) {
    let calculator = calculators.get(state);
    if (!calculator) {
        calculator = new Virtualizer({
            count: state.count,
            getScrollElement: () => null,
            estimateSize: () => state.rowHeight,
            overscan: state.overscan,
            observeElementRect: () => {},
            observeElementOffset: () => {},
            scrollToFn: () => {}
        });
        calculator.fixtureRowHeight = state.rowHeight;
        calculators.set(state, calculator);
    }
    if (calculator.options.count !== state.count || calculator.options.overscan !== state.overscan || calculator.fixtureRowHeight !== state.rowHeight) {
        calculator.setOptions({...calculator.options, count: state.count, overscan: state.overscan});
        calculator.fixtureRowHeight = state.rowHeight;
        calculator.measure();
    }
    calculator.scrollRect ??= {width: 640, height};
    calculator.scrollRect.height = height;
    calculator.scrollOffset = top;
    const items = calculator.getVirtualItems();
    state.desiredFirst = items.length ? items[0].index : 0;
    state.desiredEnd = items.length ? items[items.length - 1].index + 1 : 0;
}
