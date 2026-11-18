/**
 * Session-owned browser registries. Weak maps release disposed native nodes; detached roots are explicitly owned.
 */
export const nativeListeners = new WeakMap();
/**
 * Per-element viewport state.
 */
export const virtualLists = new WeakMap();
/**
 * Last synchronized control values.
 */
export const valueSnapshots = new WeakMap();
/**
 * Controls awaiting normalization readback.
 */
export const pendingValueSync = new Set();
/**
 * Untouched single selects waiting for their complete initial option subtree.
 */
export const pendingSelectDefaults = new WeakSet();
/**
 * Explicit browser/application selections that initial normalization must preserve.
 */
export const explicitSelectValues = new WeakSet();
/**
 * Controls whose silent readback may have reached Java before a later initial-value packet.
 */
export const normalizedValues = new WeakSet();

/**
 * Explicit single-select property requests replayed after initial option properties arrive.
 */
export const selectPropertyRequests = new WeakMap();
/**
 * Explicitly retained detached subtrees.
 */
export const detachedRoots = new Map();
