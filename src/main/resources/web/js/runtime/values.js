/**
 * Native control value assignment and silent browser normalization.
 */
import {valueSnapshots, pendingValueSync, pendingSelectDefaults, explicitSelectValues, normalizedValues, selectPropertyRequests} from './state.js';
import {sendPacket} from './transport.js';
import {SEND_VALUE_SYNC} from './protocol.js';
/**
 * Reads native scalar, checked, or losslessly encoded multiple-selection state.
 */
export function browserValue(element) {
    if (element.type === 'checkbox' || element.type === 'radio') return String(element.checked);
    if (element.tagName === 'SELECT' && element.multiple) {
        let result = '';
        for (let i = 0; i < element.selectedOptions.length; i++) {
            if (i) result += ',';
            result += 'v' + encodeURIComponent(element.selectedOptions[i].value);
        }
        return result;
    }
    return element.value == null ? '' : String(element.value);
}

/**
 * Schedules silent synchronization for browser-updated radio peers.
 */
export function synchronizeRadioPeers(element) {
    if (element.type !== 'radio' || !element.name) return;
    for (const peer of element.getRootNode().querySelectorAll('input[type="radio"][name="' + CSS.escape(element.name) + '"]')) {
        if (peer !== element && peer.name === element.name && peer.form === element.form && valueSnapshots.has(peer)) pendingValueSync.add(peer);
    }
}

/**
 * Assigns requested state and schedules browser-authoritative normalization.
 */
export function setBrowserValue(element, value, initial = false) {
    rememberSelectSelection(element, 'value', value);
    const text = value == null ? '' : String(value);
    valueSnapshots.set(element, text);
    if (element.type === 'checkbox' || element.type === 'radio') element.checked = text === 'true';
    else if (element.tagName === 'SELECT' && element.multiple) {
        const keys = new Set(text ? text.split(',').map((token) => decodeURIComponent(token.slice(1))) : []);
        for (const option of element.options) option.selected = keys.has(option.value);
    } else element.value = text;
    pendingValueSync.add(element);
    synchronizeRadioPeers(element);
    // Earlier packet normalization can already have changed Java; acknowledge this final assignment.
    if (initial && normalizedValues.delete(element)) valueSnapshots.set(element, null);
}

/**
 * Sends only browser values that differ from their last synchronized snapshot.
 */
export function flushValueSynchronization() {
    for (const element of pendingValueSync) {
        if (!valueSnapshots.has(element)) continue;
        initializeSelectDefault(element);
        const actual = browserValue(element);
        if (actual !== valueSnapshots.get(element)) {
            valueSnapshots.set(element, actual);
            sendPacket(SEND_VALUE_SYNC, Number(element.id), actual);
            normalizedValues.add(element);
        }
    }
    pendingValueSync.clear();
}

/**
 * Marks an explicitly assigned or user-selected value, including selected options.
 * Initialization must not replace an application choice merely because other options arrived later.
 */
export function rememberSelectSelection(element, property, requested = element[property]) {
    if (element.tagName === 'SELECT' && (property === 'value' || property === 'selectedIndex')) {
        explicitSelectValues.add(element);
        selectPropertyRequests.set(element, {property, value: requested});
    } else if (element.tagName === 'OPTION' && ((property === 'selected' && element.selected) || (property === 'defaultSelected' && element.defaultSelected))) {
        const select = element.closest('select');
        if (select) explicitSelectValues.add(select);
    }
}

/**
 * Defers native default selection until ordered option creation and properties have completed.
 * Empty selects stay pending until their first populated normalization batch; old clients may safely
 * ignore the additive initialization command and retain their previous native construction behavior.
 */
export function requestSelectDefault(element, serverValue) {
    // Listener installation may observe an already selected option; compare against Java's value.
    valueSnapshots.set(element, normalizedValues.delete(element) ? null : serverValue == null ? '' : String(serverValue));
    pendingSelectDefaults.add(element);
    pendingValueSync.add(element);
}

/**
 * Selects the first enabled option only for an untouched initial single select. Explicit value or
 * option choices win, disabled groups are skipped, and later insertion preserves existing selection.
 */
function initializeSelectDefault(element) {
    if (!pendingSelectDefaults.has(element)) return;
    if (explicitSelectValues.has(element)) {
        const request = selectPropertyRequests.get(element);
        if (request) element[request.property] = request.value;
        selectPropertyRequests.delete(element);
        pendingSelectDefaults.delete(element);
        explicitSelectValues.delete(element);
        return;
    }
    if (!element.options.length) return;
    if (element.multiple || element.size > 1) {
        element.selectedIndex = -1;
        pendingSelectDefaults.delete(element);
        return;
    }
    let first = -1;
    for (let index = 0; index < element.options.length; index++) {
        const option = element.options[index];
        if (!option.disabled && !(option.parentElement.tagName === 'OPTGROUP' && option.parentElement.disabled)) {
            first = index;
            break;
        }
    }
    element.selectedIndex = first;
    pendingSelectDefaults.delete(element);
}

/**
 * Schedules affected controls and enclosing selects after DOM changes.
 */
export function markValueSynchronization(element) {
    if (valueSnapshots.has(element)) pendingValueSync.add(element);
    const select = element.tagName === 'SELECT' ? element : element.closest?.('select');
    if (select && valueSnapshots.has(select)) pendingValueSync.add(select);
}
