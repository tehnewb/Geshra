/**
 * Native DOM mutation application. Server ordering preserves creation, property, and method dependencies.
 */
import {
    SET_TITLE,
    SET_TEXT,
    SET_HTML,
    SET_ATTRIBUTE,
    SET_PROPERTY,
    SET_CLASS,
    SET_STYLE,
    SET_VALUE,
    APPEND_CHILD,
    REMOVE,
    INSERT_BEFORE,
    INSERT_AFTER,
    CLEAR_CHILDREN,
    ADD_EVENT,
    REMOVE_EVENT,
    TRIGGER_EVENT,
    TOGGLE_CLASS,
    SET_DATASET,
    FOCUS,
    BLUR,
    SCROLL_TO,
    ADD_CLASS,
    REMOVE_CLASS,
    SET_TYPE,
    TEXT,
    HTML,
    KEY,
    VALUE,
    PROPERTY,
    CLASS_NAME,
    STYLE_PROP,
    STYLE_VAL,
    EVENT_NAME,
    DATASET_KEY,
    DATASET_VAL,
    TOP,
    LEFT,
    BEHAVIOR,
    IDENTIFIER,
    TYPE,
    INSERT_ID,
    BOOLEAN_PROPERTIES
} from './protocol.js';
import {virtualLists, detachedRoots} from './state.js';
import {createRuntimeElement, setRuntimeText, setRuntimeHTML, setRuntimeAttribute} from './elements.js';
import {findRuntimeElement, cleanupRuntimeTree, resolveBrowserArguments} from './lifecycle.js';
import {setBrowserValue, markValueSynchronization, synchronizeRadioPeers, rememberSelectSelection, requestSelectDefault} from './values.js';
import {addRuntimeListener, removeRuntimeListener} from './events.js';
import {configureVirtualList, acknowledgeVirtualWindow} from './virtual-list.js';
/**
 * Applies one ordered server mutation using native browser operations.
 */
export function updateDOM(type, id, params) {
    const element = findRuntimeElement(id);

    if (!element) return;

    switch (type) {
        case 35: {
            const metadata = JSON.parse(params[VALUE]);
            for (const node of document.head.querySelectorAll('[data-geshra-seo]')) node.remove();
            document.title = metadata.title;
            if (metadata.head) {
                for (const title of document.head.querySelectorAll('title')) title.remove();
                document.head.insertAdjacentHTML('beforeend', metadata.head);
            }
            document.documentElement.lang = metadata.language;
            if (metadata.noIndex) {
                const robots = document.createElement('meta');
                robots.dataset.geshraSeo = '';
                robots.name = 'robots';
                robots.content = 'noindex,nofollow';
                document.head.appendChild(robots);
            }
            break;
        }
        case 25: {
            const value = resolveBrowserArguments(JSON.parse(params[VALUE]));
            element[params[KEY]] = value;
            rememberSelectSelection(element, params[KEY], value);
            markValueSynchronization(element);
            synchronizeRadioPeers(element);
            break;
        }
        case 26: {
            const args = resolveBrowserArguments(JSON.parse(params[VALUE]));
            try {
                const result = element[params[KEY]](...args);
                if (result && typeof result.catch === 'function') result.catch((error) => console.error('Native method failed', error));
            } catch (error) {
                console.error('Native method failed', error);
            }
            break;
        }
        case 27:
            configureVirtualList(element, JSON.parse(params[VALUE]));
            break;
        case 28: {
            const window = JSON.parse(params[VALUE]);
            const state = virtualLists.get(element);
            if (state) acknowledgeVirtualWindow(state, window);
            break;
        }
        case 29:
            cleanupRuntimeTree(element, true);
            detachedRoots.set(String(id), element);
            element.remove();
            break;
        case 30: {
            const child = findRuntimeElement(params[IDENTIFIER]);
            const before = params[INSERT_ID] == null ? null : findRuntimeElement(params[INSERT_ID]);
            element.insertBefore(child, before);
            detachedRoots.delete(String(child.id));
            const walker = document.createTreeWalker(child, NodeFilter.SHOW_ELEMENT);
            let node = child;
            do {
                const state = virtualLists.get(node);
                if (state) {
                    state.pending.length = 0;
                    state.awaitingInitialWindow = false;
                    state.requestedFirst = -1;
                    state.requestedEnd = -1;
                    state.observer.observe(node);
                    state.schedule();
                }
            } while ((node = walker.nextNode()));
            break;
        }
        case 31:
            element.removeAttribute(params[KEY]);
            markValueSynchronization(element);
            break;
        case 32:
        case 33: {
            try {
                const context = element.getContext('2d');
                const value = resolveBrowserArguments(JSON.parse(params[VALUE]));
                if (type === 32) context[params[KEY]](...value);
                else context[params[KEY]] = value;
            } catch (error) {
                console.error('Canvas command failed', error);
            }
            break;
        }
        case SET_TITLE:
            document.title = params[TEXT];
            break;
        case SET_TEXT:
            setRuntimeText(element, params[TEXT]);
            break;
        case SET_HTML:
            setRuntimeHTML(element, params[HTML]);
            break;
        case SET_ATTRIBUTE:
            setRuntimeAttribute(element, params[KEY], params[VALUE]);
            rememberSelectSelection(element, params[KEY], params[VALUE]);
            markValueSynchronization(element);
            synchronizeRadioPeers(element);
            break;
        case SET_PROPERTY:
            const prop = params[PROPERTY];
            const value = params[VALUE];

            if (BOOLEAN_PROPERTIES.has(prop)) {
                element[prop] = value === 'true';
            } else {
                element[prop] = value;
            }
            rememberSelectSelection(element, prop, value);
            markValueSynchronization(element);
            synchronizeRadioPeers(element);
            break;
        case 36:
            requestSelectDefault(element, params[VALUE]);
            break;
        case SET_CLASS:
            element.setAttribute('class', params[CLASS_NAME]);
            break;
        case SET_STYLE:
            element.style.setProperty(params[STYLE_PROP], params[STYLE_VAL]);
            break;
        case 34:
            setBrowserValue(element, params[VALUE], true);
            break;
        case SET_VALUE:
            setBrowserValue(element, params[VALUE]);
            break;
        case INSERT_BEFORE: {
            const child = findRuntimeElement(params[INSERT_ID]);
            const toInsert = createRuntimeElement(params[HTML], params[IDENTIFIER], element);

            element.insertBefore(toInsert, child);
            break;
        }
        case INSERT_AFTER: {
            const child = findRuntimeElement(params[INSERT_ID]);
            const toInsert = createRuntimeElement(params[HTML], params[IDENTIFIER], element);

            element.insertBefore(toInsert, child.nextSibling);
            break;
        }
        case APPEND_CHILD: {
            const child = createRuntimeElement(params[HTML], params[IDENTIFIER], element);
            element.appendChild(child);
            break;
        }
        case REMOVE:
            handOffVirtualFocus(element);
            markValueSynchronization(element.parentElement || element);
            cleanupRuntimeTree(element);
            element.remove();
            break;
        case CLEAR_CHILDREN:
            handOffVirtualFocus(element);
            for (const child of element.children) cleanupRuntimeTree(child);
            element.replaceChildren();
            markValueSynchronization(element);
            break;
        case ADD_EVENT: {
            addRuntimeListener(element, params[EVENT_NAME], params[VALUE] ? JSON.parse(params[VALUE]) : {typed: true});
            break;
        }
        case REMOVE_EVENT:
            removeRuntimeListener(element, params[EVENT_NAME], params[VALUE]);
            break;
        case TRIGGER_EVENT:
            element.dispatchEvent(new Event(params[EVENT_NAME], {bubbles: true}));
            break;
        case TOGGLE_CLASS:
            element.classList.toggle(params[CLASS_NAME]);
            break;
        case SET_DATASET:
            element.dataset[params[DATASET_KEY]] = params[DATASET_VAL];
            break;
        case FOCUS:
            element.focus();
            break;
        case BLUR:
            element.blur();
            break;
        case SCROLL_TO: {
            const topParam = parseInt(params[TOP]);
            const leftParam = parseInt(params[LEFT]);
            const behavior = params[BEHAVIOR] || 'auto';

            element.scrollTo({top: topParam, left: leftParam, behavior});

            break;
        }
        case ADD_CLASS:
            element.classList.add(params[CLASS_NAME]);
            break;
        case REMOVE_CLASS:
            element.classList.remove(params[CLASS_NAME]);
            break;
        case SET_TYPE:
            element.type = params[TYPE];
            markValueSynchronization(element);
            break;
    }
}

/**
 * Moves focus to the owning list before its focused row is disposed. Native input events already
 * precede the viewport request on the same socket; application-owned edits remain authoritative.
 */
function handOffVirtualFocus(element) {
    const active = document.activeElement;
    if (!active || !element.contains(active)) return;
    const list = element.closest('.geshra-virtual-list');
    if (list && list !== element && !element.contains(list)) list.focus({preventScroll: true});
}
