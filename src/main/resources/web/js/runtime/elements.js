/**
 * Native node creation and trusted route script activation.
 */
import {cleanupRuntimeTree} from './lifecycle.js';
import {markValueSynchronization} from './values.js';
/**
 * Creates an HTML or SVG node with its session identifier.
 */
export function createRuntimeElement(tagName, id, parent) {
    const svg = tagName === 'svg' || (parent?.namespaceURI === 'http://www.w3.org/2000/svg' && parent.localName !== 'foreignObject');
    const element = svg ? document.createElementNS('http://www.w3.org/2000/svg', tagName) : document.createElement(tagName);
    element.id = id;

    if (String(tagName).toLowerCase() === 'script') {
        element.dataset.pmPendingScript = 'true';
    }

    return element;
}

/**
 * Replaces text while releasing managed descendants and activating trusted script content.
 */
export function setRuntimeText(element, value) {
    if (isScriptElement(element)) {
        activateInlineScript(element, value || '');
        return;
    }

    for (const child of element.children) cleanupRuntimeTree(child);
    element.textContent = value;
    markValueSynchronization(element);
}

/**
 * Replaces trusted HTML and releases managed descendants before browser normalization.
 */
export function setRuntimeHTML(element, value) {
    if (isScriptElement(element)) {
        activateInlineScript(element, value || '');
        return;
    }

    for (const child of element.children) cleanupRuntimeTree(child);
    element.innerHTML = value;
    markValueSynchronization(element);
}

/**
 * Assigns a native attribute, activating script sources through a replacement node.
 */
export function setRuntimeAttribute(element, key, value) {
    if (isScriptElement(element) && String(key).toLowerCase() === 'src') {
        activateExternalScript(element, value);
        return;
    }

    element.setAttribute(key, value);
}

/**
 * Identifies script placeholders and activated script elements.
 */
function isScriptElement(element) {
    return element && element.tagName && element.tagName.toLowerCase() === 'script';
}

/**
 * Copies script configuration while preserving source activation order.
 */
function copyScriptAttributes(from, to, skipSrc) {
    for (const attribute of Array.from(from.attributes || [])) {
        const name = attribute.name.toLowerCase();

        if (name === 'data-geshra-pending-script') {
            continue;
        }

        if (skipSrc && name === 'src') {
            continue;
        }

        to.setAttribute(attribute.name, attribute.value);
    }
}

/**
 * Preserves explicit asynchronous and deferred execution policy.
 */
function configureScriptExecutionMode(placeholder, script) {
    if (placeholder.hasAttribute('async')) {
        script.async = true;
        return;
    }

    if (placeholder.hasAttribute('defer')) {
        script.defer = true;
        script.async = false;
        return;
    }

    script.async = false;
}

/**
 * Notifies application scripts after route script activation.
 */
function notifyRouteScriptLoaded(src, script) {
    document.dispatchEvent(
        new CustomEvent('geshra:route-script-loaded', {
            detail: {
                src: src || '',
                script: script
            }
        })
    );
}

/**
 * Replaces an inert placeholder with a browser-executable external script.
 */
function activateExternalScript(placeholder, src) {
    if (!src) {
        placeholder.removeAttribute('src');
        return;
    }

    const script = document.createElement('script');
    copyScriptAttributes(placeholder, script, true);
    script.id = placeholder.id;
    configureScriptExecutionMode(placeholder, script);

    script.addEventListener(
        'load',
        () => {
            notifyRouteScriptLoaded(src, script);
        },
        {once: true}
    );

    script.addEventListener(
        'error',
        () => {
            console.error('Unable to load route script:', src);
        },
        {once: true}
    );

    script.src = src;

    if (placeholder.parentNode) {
        placeholder.parentNode.replaceChild(script, placeholder);
    } else {
        placeholder.setAttribute('src', src);
    }
}

/**
 * Replaces an inert placeholder with trusted inline application code.
 */
function activateInlineScript(placeholder, code) {
    const script = document.createElement('script');
    copyScriptAttributes(placeholder, script, true);
    script.id = placeholder.id;
    configureScriptExecutionMode(placeholder, script);
    script.text = code;

    if (placeholder.parentNode) {
        placeholder.parentNode.replaceChild(script, placeholder);
        notifyRouteScriptLoaded('', script);
    } else {
        placeholder.text = code;
    }
}
