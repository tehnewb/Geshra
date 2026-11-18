/**
 * Computes a half-open fixed-height window in constant space without per-row metadata.
 * State is caller-owned; no DOM reads or result-array allocations occur here.
 */
export function calculateVirtualRange(state, top, height) {
    state.desiredFirst = Math.max(0, Math.min(state.count, Math.floor(top / state.rowHeight) - state.overscan));
    state.desiredEnd = Math.min(state.count, state.desiredFirst + 4096, Math.ceil((top + height) / state.rowHeight) + state.overscan);
}
