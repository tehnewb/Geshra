/**
 * Synchronous default-action control for Geshra-owned keyboard widgets. Server callbacks retain
 * selection/state ownership; Tab, composing input and browser shortcuts are never trapped.
 */
/**
 * Navigation and activation keys handled by tree widgets.
 */
const TREE_KEYS = new Set(['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End', 'Enter', ' ']);
/**
 * Navigation keys handled by horizontal tab lists.
 */
const TAB_KEYS = new Set(['ArrowLeft', 'ArrowRight', 'Home', 'End']);

/**
 * Suppresses duplicate ancestor forwarding and native scrolling only for a widget's handled keys.
 * @returns {boolean} whether the existing typed key forwarding should continue
 */
export function prepareCompositeKey(element, event) {
    const kind = element.dataset.geshraKeyboard;
    if (kind !== 'tree' && kind !== 'tabs') return true;
    if (event.target !== element || event.isComposing) return false;
    if (event.altKey || event.metaKey) return true;
    if ((kind === 'tree' ? TREE_KEYS : TAB_KEYS).has(event.key)) event.preventDefault();
    return true;
}
