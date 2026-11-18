/**
 * Computes the half-open fixed-height window in constant space, without per-row metadata.
 * State is caller-owned; this function performs no DOM reads and allocates no result array.
 */
export {calculateVirtualRange} from '../../src/main/resources/web/js/runtime/virtual-range.js';
