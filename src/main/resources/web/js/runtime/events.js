/**
 * Native event subscriptions with one forwarding listener per element and event name.
 */
import {SEND_CLICK, SEND_KEY_UP, SEND_KEY_DOWN, SEND_VALUE_CHANGE, SEND_SUBMIT, SEND_FILE, SEND_DOM_EVENT} from './protocol.js';
import {nativeListeners, valueSnapshots, pendingValueSync} from './state.js';
import {browserValue, synchronizeRadioPeers, flushValueSynchronization, rememberSelectSelection} from './values.js';
import {sendPacket} from './transport.js';
import {prepareCompositeKey} from './composite-keys.js';
/**
 * Installs one native forwarding callback per element and event name.
 */
export function addRuntimeListener(element, name, options) {
    let bindings = nativeListeners.get(element);
    if (!bindings) nativeListeners.set(element, (bindings = new Map()));
    let binding = bindings.get(name);
    if (binding) {
        binding.typed ||= !!options.typed;
        binding.generic ||= !!options.generic;
        if (options.generic) binding.preventDefault = !!options.preventDefault;
        return;
    }
    binding = {typed: !!options.typed, generic: !!options.generic, preventDefault: !!options.preventDefault};
    binding.listener = (event) => {
        if (name === 'keydown' && !prepareCompositeKey(element, event)) return;
        const id = Number(element.id);
        if (binding.preventDefault || (name === 'submit' && binding.typed)) event.preventDefault();
        if (binding.typed) {
            switch (name) {
                case 'click':
                    sendPacket(
                        SEND_CLICK,
                        id,
                        event.button,
                        event.clientX,
                        event.clientY,
                        event.pageX,
                        event.pageY,
                        event.screenX,
                        event.screenY,
                        event.ctrlKey,
                        event.shiftKey,
                        event.altKey,
                        event.metaKey
                    );
                    break;
                case 'keyup':
                    sendPacket(SEND_KEY_UP, id, event.key, event.repeat, event.ctrlKey, event.shiftKey, event.altKey, event.metaKey);
                    break;
                case 'keydown':
                    sendPacket(SEND_KEY_DOWN, id, event.key, event.repeat, event.ctrlKey, event.shiftKey, event.altKey, event.metaKey);
                    break;
                case 'input': {
                    const value = browserValue(element);
                    rememberSelectSelection(element, 'value');
                    valueSnapshots.set(element, value);
                    // Synchronize native radio unchecks before application callbacks read the group.
                    synchronizeRadioPeers(element);
                    flushValueSynchronization();
                    sendPacket(SEND_VALUE_CHANGE, id, value);
                    break;
                }
                case 'submit':
                    sendPacket(SEND_SUBMIT, id);
                    break;
                case 'change': {
                    if (element.type === 'file')
                        for (const file of element.files) {
                            if (file.size > 65536) {
                                console.error('Upload exceeds the WebSocket frame limit');
                                continue;
                            }
                            const reader = new FileReader();
                            reader.onload = () => sendPacket(SEND_FILE, id, file.name, reader.result);
                            reader.readAsArrayBuffer(file);
                        }
                    break;
                }
            }
        }
        if (binding.generic) {
            let value = browserValue(element);
            if (element.tagName === 'DIALOG' && name === 'close') value = element.returnValue;
            else if (name === 'toggle') value = event.newState || (element.open ? 'open' : 'closed');
            sendPacket(SEND_DOM_EVENT, id, name, value);
        }
    };
    bindings.set(name, binding);
    element.addEventListener(name, binding.listener, {passive: false});
    if (options.typed && name === 'input') {
        valueSnapshots.set(element, browserValue(element));
        pendingValueSync.add(element);
    }
}

/**
 * Removes a forwarding mode and releases listeners with no subscribers.
 */
export function removeRuntimeListener(element, name, mode) {
    const bindings = nativeListeners.get(element);
    const binding = bindings?.get(name);
    if (!binding) return;
    if (mode === 'generic') {
        binding.generic = false;
        binding.preventDefault = false;
    } else binding.typed = false;
    if (!binding.typed && !binding.generic) {
        element.removeEventListener(name, binding.listener);
        bindings.delete(name);
    }
}
