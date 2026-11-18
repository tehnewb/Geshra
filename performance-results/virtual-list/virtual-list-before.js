/**
 * Bounded fixed-height list scrolling with animation-frame-coalesced viewport requests.
 */
import {virtualLists} from './state.js';
import {findRuntimeElement} from './lifecycle.js';
import {sendPacket} from './transport.js';
import {SEND_VIEWPORT} from './protocol.js';
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
function requestVirtualWindow(state, force = false) {
    if (!state.element.isConnected) return;
    const viewport = updateVirtualPosition(state);
    const first = Math.max(0, Math.floor(viewport.logicalTop / state.rowHeight) - state.overscan);
    const end = Math.min(state.count, first + 4096, Math.ceil((viewport.logicalTop + viewport.height) / state.rowHeight) + state.overscan);
    if (force || first !== state.requestedFirst || end !== state.requestedEnd) {
        state.requestedFirst = first;
        state.requestedEnd = end;
        sendPacket(SEND_VIEWPORT, Number(state.element.id), state.revision, first, end);
    }
}

/**
 * Installs coalesced scroll and resize observation for a virtual list.
 */
export function configureVirtualList(element, configuration) {
    let state = virtualLists.get(element);
    if (!state) {
        state = {element, first: 0, raf: 0};
        state.schedule = () => {
            if (!state.raf)
                state.raf = requestAnimationFrame(() => {
                    state.raf = 0;
                    requestVirtualWindow(state);
                });
        };
        state.scroll = state.schedule;
        state.observer = new ResizeObserver(state.schedule);
        element.addEventListener('scroll', state.scroll, {passive: true});
        virtualLists.set(element, state);
    }
    Object.assign(state, configuration);
    state.spacer = findRuntimeElement(configuration.spacer);
    state.layer = findRuntimeElement(configuration.layer);
    state.spacer.style.height = Math.min(state.count * state.rowHeight, 8000000) + 'px';
    state.requestedFirst = -1;
    state.requestedEnd = -1;
    state.observer.observe(element);
    state.schedule();
    element.fsiScrollToIndex = (index) => {
        const height = element.clientHeight;
        const logical = state.count * state.rowHeight;
        const physical = Math.min(logical, 8000000);
        const ratio = logical > height && physical > height ? (logical - height) / (physical - height) : 1;
        element.scrollTop = (index * state.rowHeight) / ratio;
        state.schedule();
    };
}
