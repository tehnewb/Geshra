/**
 * Bounded fixed-height list scrolling with animation-frame-coalesced viewport requests.
 */
import {virtualLists} from './state.js';
import {findRuntimeElement} from './lifecycle.js';
import {sendViewportPacket, viewportConnectionReady} from './transport.js';
import {calculateVirtualRange} from './virtual-range.js';
/**
 * Brief scroll quiescence reserves the second slot for the latest terminal viewport instead of
 * filling both slots together during continuous scrolling. This is not a network timeout.
 */
const VIRTUAL_SCROLL_SETTLE_MS = 32;
/**
 * Maps bounded physical scrolling to logical row coordinates.
 */
export function updateVirtualPosition(state) {
    const height = state.element.clientHeight;
    const logical = state.count * state.rowHeight;
    const physical = Math.min(logical, 8000000);
    const ratio = logical > height && physical > height ? (logical - height) / (physical - height) : 1;
    const logicalTop = state.element.scrollTop * ratio;
    const transform = 'translateY(' + (state.element.scrollTop + state.first * state.rowHeight - logicalTop) + 'px)';
    if (transform !== state.transform) state.layer.style.transform = transform;
    state.transform = transform;
    return {logicalTop, height};
}

/**
 * Sends a viewport only when its requested row range changes.
 */
function requestVirtualWindow(state) {
    if (!state.element.isConnected) return;
    const viewport = updateVirtualPosition(state);
    calculateVirtualRange(state, viewport.logicalTop, viewport.height);
    const first = state.desiredFirst;
    const end = state.desiredEnd;
    if (state.priority && state.element.scrollTop !== state.priorityTop) state.priority = false;
    if (first === state.requestedFirst && end === state.requestedEnd) {
        state.priority = false;
        return;
    }
    const configuredLimit = state.requestAware ? state.maxPending || 1 : 1;
    const limit = state.priority || !state.scrolling ? configuredLimit : 1;
    if (state.pending.length >= limit || state.awaitingInitialWindow || !viewportConnectionReady()) return;
    protectEvictedRows(state, first, end);
    state.nextToken = ((state.nextToken || 0) % 2147483647) + 1;
    const token = state.requestAware ? state.nextToken : undefined;
    if (sendViewportPacket(Number(state.element.id), state.revision, first, end, token)) {
        state.requestedFirst = first;
        state.requestedEnd = end;
        state.pending.push({token, first, end});
        state.priority = false;
    }
}

/**
 * Installs coalesced scroll and resize observation for a virtual list.
 */
export function configureVirtualList(element, configuration) {
    let state = virtualLists.get(element);
    if (!state) {
        state = {element, first: 0, raf: 0, pending: []};
        state.schedule = () => {
            if (!state.raf)
                state.raf = requestAnimationFrame(() => {
                    state.raf = 0;
                    requestVirtualWindow(state);
                });
        };
        state.scroll = () => {
            if (state.requestAware && state.maxPending === 2) {
                state.scrolling = true;
                if (state.scrollTimer) clearTimeout(state.scrollTimer);
                state.scrollTimer = setTimeout(() => {
                    state.scrollTimer = 0;
                    state.scrolling = false;
                    state.schedule();
                }, VIRTUAL_SCROLL_SETTLE_MS);
            }
            state.schedule();
        };
        state.observer = new ResizeObserver(state.schedule);
        element.addEventListener('scroll', state.scroll, {passive: true});
        virtualLists.set(element, state);
    }
    Object.assign(state, configuration);
    state.requestAware = configuration.requestAware === true;
    state.maxPending = configuration.maxPending || 1;
    state.spacer = findRuntimeElement(configuration.spacer);
    state.layer = findRuntimeElement(configuration.layer);
    state.spacer.style.height = Math.min(state.count * state.rowHeight, 8000000) + 'px';
    state.pending.length = 0;
    resetVirtualScrollState(state);
    state.awaitingInitialWindow = true;
    state.failureCount = 0;
    state.requestedFirst = configuration.first ?? -1;
    state.requestedEnd = configuration.end ?? -1;
    state.observer.observe(element);
    state.schedule();
    element.fsiScrollToIndex = (index) => {
        const height = element.clientHeight;
        const logical = state.count * state.rowHeight;
        const physical = Math.min(logical, 8000000);
        const ratio = logical > height && physical > height ? (logical - height) / (physical - height) : 1;
        element.scrollTop = (index * state.rowHeight) / ratio;
        state.priority = true;
        state.priorityTop = element.scrollTop;
        state.schedule();
    };
}

/**
 * Releases the per-list quiescence timer on configuration, retained detach, disposal or recovery.
 */
export function resetVirtualScrollState(state) {
    if (state.scrollTimer) clearTimeout(state.scrollTimer);
    state.scrollTimer = 0;
    state.scrolling = false;
    state.priority = false;
}

/**
 * Commits an ordered server acknowledgment and sends only the latest changed desired range.
 * Structural DOM mutations are never discarded; server component IDs remain authoritative.
 */
export function acknowledgeVirtualWindow(state, window) {
    if (state.revision !== window.revision) return;
    if (state.awaitingInitialWindow) {
        if (state.requestAware && window.requested !== false) return;
        state.awaitingInitialWindow = false;
    } else {
        if (state.requestAware && window.requested !== true) return;
        const requestedFirst = window.requestFirst ?? window.first;
        const requestedEnd = window.requestEnd ?? window.end;
        let index = 0;
        while (index < state.pending.length) {
            const request = state.pending[index];
            if ((!state.requestAware || request.token === window.token) && request.first === requestedFirst && request.end === requestedEnd) break;
            index++;
        }
        if (index === state.pending.length) return;
        state.pending[index] = state.pending[state.pending.length - 1];
        state.pending.pop();
    }
    state.first = window.first;
    for (const row of state.layer.children) row.inert = false;
    for (const request of state.pending) protectEvictedRows(state, request.first, request.end);
    if (window.failed) {
        state.failureCount = state.failureFirst === window.requestFirst && state.failureEnd === window.requestEnd ? state.failureCount + 1 : 1;
        state.failureFirst = window.requestFirst;
        state.failureEnd = window.requestEnd;
        if (state.failureCount === 1 && (!window.requested || (state.requestedFirst === window.requestFirst && state.requestedEnd === window.requestEnd))) {
            state.requestedFirst = -1;
            state.requestedEnd = -1;
        }
        state.element.dispatchEvent(new CustomEvent('geshra-virtual-error', {detail: window}));
    } else if (window.requestFirst === state.failureFirst && window.requestEnd === state.failureEnd) state.failureCount = 0;
    updateVirtualPosition(state);
    state.schedule();
}

/**
 * Prevents native interaction with rows that the server is about to dispose. Focus and pending
 * input/blur handlers complete before the viewport send, preserving their WebSocket ordering.
 */
function protectEvictedRows(state, first, end) {
    const active = document.activeElement;
    for (const row of state.layer.children) {
        const index = Number(row.getAttribute('aria-posinset')) - 1;
        if (index >= first && index < end) continue;
        if (active && row.contains(active)) state.element.focus({preventScroll: true});
        row.inert = true;
    }
}

/**
 * Resynchronizes connected lists after session recovery without retaining stale viewport packets.
 * A document scan happens only on reconnect; detached lists resume on reattachment instead.
 */
export function resumeVirtualLists() {
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_ELEMENT);
    let node = document.body;
    do {
        const state = virtualLists.get(node);
        if (state) {
            state.pending.length = 0;
            resetVirtualScrollState(state);
            state.awaitingInitialWindow = false;
            state.requestedFirst = -1;
            state.requestedEnd = -1;
            state.schedule();
        }
    } while ((node = walker.nextNode()));
}
