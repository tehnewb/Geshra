/**
 * Retained-node lookup, descendant cleanup, and component-reference resolution.
 */
import {
    nativeListeners,
    virtualLists,
    valueSnapshots,
    pendingValueSync,
    pendingSelectDefaults,
    explicitSelectValues,
    normalizedValues,
    selectPropertyRequests,
    detachedRoots
} from './state.js';
/**
 * Resolves a session node, including descendants retained outside the document.
 */
export function findRuntimeElement(id) {
    const name = String(id);
    const attached = document.getElementById(name);
    if (attached) return attached;
    const root = detachedRoots.get(name);
    if (root) return root;
    for (const element of detachedRoots.values()) {
        const child = element.querySelector('[id="' + name + '"]');
        if (child) return child;
    }
    return null;
}

/**
 * Releases observers and listeners, preserving explicitly retained subtrees.
 */
export function cleanupRuntimeTree(element, retained = false) {
    const walker = document.createTreeWalker(element, NodeFilter.SHOW_ELEMENT);
    let node = element;
    do {
        const state = virtualLists.get(node);
        if (state) {
            if (state.raf) cancelAnimationFrame(state.raf);
            state.raf = 0;
            state.observer.disconnect();
            if (!retained) {
                state.pending.length = 0;
                node.removeEventListener('scroll', state.scroll);
                virtualLists.delete(node);
            }
        }
        pendingValueSync.delete(node);
        if (!retained) {
            const bindings = nativeListeners.get(node);
            if (bindings) for (const [name, binding] of bindings) node.removeEventListener(name, binding.listener);
            nativeListeners.delete(node);
            valueSnapshots.delete(node);
            pendingSelectDefaults.delete(node);
            explicitSelectValues.delete(node);
            normalizedValues.delete(node);
            selectPropertyRequests.delete(node);
            detachedRoots.delete(String(node.id));
        }
    } while ((node = walker.nextNode()));
}

/**
 * Resolves serialized component references to their native browser nodes.
 */
export function resolveBrowserArguments(value) {
    if (Array.isArray(value)) return value.map(resolveBrowserArguments);
    if (value && typeof value === 'object') {
        if (Object.keys(value).length === 1 && '$element' in value) return findRuntimeElement(value.$element);
        for (const key of Object.keys(value)) value[key] = resolveBrowserArguments(value[key]);
    }
    return value;
}
