/**
 * runtime.js
 *
 * Client-side runtime that:
 *  - Opens a WebSocket connection to the server (/ws endpoint).
 *  - Serializes user interactions (navigation, clicks, key events, form inputs, file uploads) into binary packets.
 *  - Sends those packets over the socket.
 *  - Receives binary packets from the server to update:
 *      • document title
 *      • cookies
 *      • history/navigation
 *      • arbitrary DOM changes (text, HTML, attributes, events, etc.).
 */
(() => {
    /**
     * Outgoing packet opcodes.
     * These codes identify the type of user action sent from client → server.
     */
    const SEND_NAVIGATE = 0;  // Send current path on load/popstate/link click
    const SEND_CLICK = 1;  // Send mouse click details (button, coords, modifiers)
    const SEND_KEY_UP = 2;  // Send key-up event (key, repeat, modifiers)
    const SEND_KEY_DOWN = 3;  // Send key-down event (key, repeat, modifiers)
    const SEND_VALUE_CHANGE = 4;  // Send input/change value (e.g., text, checkbox)
    const SEND_SUBMIT = 5;  // Send form submit event
    const SEND_FILE = 6;  // Send file contents (filename + binary data)
    const SEND_VIEWPORT = 8;
    const SEND_DOM_EVENT = 9;
    const SEND_VALUE_SYNC = 10;
    const SEND_HEARTBEAT = 7;  // Keep the WebSocket/session alive while the page is idle

    /**
     * Incoming packet opcodes.
     * These codes identify the type of update sent from server → client.
     */
    const RECEIVE_NAVIGATE = 0;  // Instruct client to pushState() and update URL
    const RECEIVE_DOM_UPDATE = 1;  // Apply one or more dynamic DOM mutations
    const RECEIVE_COOKIE = 2;  // Set or update document.cookie
    const RECEIVE_TITLE = 3;  // Update document.title

    /** Replace the document’s title */
    const SET_TITLE = 0;
    /** Update an element’s textContent */
    const SET_TEXT = 1;
    /** Update an element’s innerHTML */
    const SET_HTML = 2;
    /** Set an attribute on an element */
    const SET_ATTRIBUTE = 3;
    /** Set a property on an element (e.g., checked, value) */
    const SET_PROPERTY = 4;
    /** Toggle an element’s className */
    const SET_CLASS = 5;
    /** Set an inline style property (name:value) */
    const SET_STYLE = 6;
    /** Update an element’s value (e.g., input value) */
    const SET_VALUE = 7;
    /** Append a newly created child node */
    const APPEND_CHILD = 8;
    /** Remove an existing node */
    const REMOVE = 9;
    /** Insert a node before another */
    const INSERT_BEFORE = 10;
    /** Insert a node after another */
    const INSERT_AFTER = 11;
    /** Replace one node with another */
    const REPLACE = 12;
    /** Remove all children from an element */
    const CLEAR_CHILDREN = 13;
    /** Add an event listener to an element */
    const ADD_EVENT = 14;
    /** Remove an event listener from an element */
    const REMOVE_EVENT = 15;
    /** Programmatically trigger an event on an element */
    const TRIGGER_EVENT = 16;
    /** Toggle a specific class on an element */
    const TOGGLE_CLASS = 17;
    /** Set a data-* attribute key */
    const SET_DATASET = 18;
    /** Focus an element */
    const FOCUS = 19;
    /** Blur (unfocus) an element */
    const BLUR = 20;
    /** Scroll an element into view (parameters: top, left, behavior) */
    const SCROLL_TO = 21;
    /** Add a class to an element’s classList */
    const ADD_CLASS = 22;
    /** Remove a class from an element’s classList */
    const REMOVE_CLASS = 23;
    /** Change an element’s type property (e.g., input type) */
    const SET_TYPE = 24;

    /** params[0] holds text values for mutations */
    const TEXT = 0;
    /** params[1] holds HTML strings for mutations */
    const HTML = 1;
    /** params[2] holds attribute/property keys */
    const KEY = 2;
    /** params[3] holds attribute/property values */
    const VALUE = 3;
    /** params[4] holds property names (for SET_PROPERTY) */
    const PROPERTY = 4;
    /** params[5] holds class names (for SET_CLASS, ADD_CLASS, REMOVE_CLASS) */
    const CLASS_NAME = 5;
    /** params[6] holds style property names */
    const STYLE_PROP = 6;
    /** params[7] holds style values */
    const STYLE_VAL = 7;
    /** params[8] holds event names (for ADD_EVENT, REMOVE_EVENT, TRIGGER_EVENT) */
    const EVENT_NAME = 8;
    /** params[9] holds boolean toggle flags */
    const CLASS_TOGGLE = 9;
    /** params[10] holds boolean “force” flags (for classList methods) */
    const FORCE = 10;
    /** params[11] holds data-* attribute keys */
    const DATASET_KEY = 11;
    /** params[12] holds data-* attribute values */
    const DATASET_VAL = 12;
    /** params[13] holds scroll-to “top” coordinate */
    const TOP = 13;
    /** params[14] holds scroll-to “left” coordinate */
    const LEFT = 14;
    /** params[15] holds scroll behavior (e.g., "smooth" or "auto") */
    const BEHAVIOR = 15;
    /** params[16] holds element identifiers (IDs) for certain ops */
    const IDENTIFIER = 16;
    /** params[17] holds generic “type” values */
    const TYPE = 17;
    /** params[18] holds the ID of the child to insert an element into **/
    const INSERT_ID = 18;

    /** cookieParams[0] = name */
    const COOKIE_NAME = 0;
    /** cookieParams[1] = value */
    const COOKIE_VALUE = 1;
    /** cookieParams[2] = path */
    const COOKIE_PATH = 2;
    /** cookieParams[3] = max-age (in seconds) */
    const COOKIE_MAX_AGE = 3;
    /** cookieParams[4] = expires (Date string) */
    const COOKIE_EXPIRES = 4;
    /** cookieParams[5] = domain */
    const COOKIE_DOMAIN = 5;
    /** cookieParams[6] = secure flag */
    const COOKIE_SECURE = 6;
    /** cookieParams[7] = HttpOnly flag */
    const COOKIE_HTTP_ONLY = 7;
    /** cookieParams[8] = SameSite policy ("Strict", "Lax", "None") */
    const COOKIE_SAME_SITE = 8;

    /** Set of boolean HTML attributes (no string value) */
    const BOOLEAN_PROPERTIES = new Set([
        "checked", "disabled", "selected",
        "readonly", "required", "autofocus", "multiple"
    ]);

    /** UTF-8 encoder for outgoing text */
    const enc = new TextEncoder();
    /** UTF-8 decoder for incoming text */
    const dec = new TextDecoder();
    /** Registry of functions that serialize outgoing packets */
    const packets_out = [];
    /** Registry of functions that parse incoming packets */
    const packets_in = [];


    /**
     * Serialize a navigation packet.
     * @param {string} path - The URL or route to navigate to.
     * @returns {Uint8Array|null} Packet: [opcode, lengthHigh, lengthLow, UTF-8 bytes...] or null if invalid.
     */
    packets_out[SEND_NAVIGATE] = (path) => {
        if (typeof path !== "string") return null;
        const bytes = enc.encode(path);
        const len = bytes.length;
        const buf = new Uint8Array(3 + len);
        buf[0] = SEND_NAVIGATE;
        buf[1] = (len >> 8) & 0xFF;
        buf[2] = len & 0xFF;
        buf.set(bytes, 3);
        return buf;
    };

    /**
     * Serialize a mouse-click packet.
     * @param {number} id       - Element identifier.
     * @param {number} btn      - Button (0=left,1=middle,2=right).
     * @param {number} cx       - Click X coordinate relative to element.
     * @param {number} cy       - Click Y coordinate relative to element.
     * @param {number} px       - Page X coordinate.
     * @param {number} py       - Page Y coordinate.
     * @param {number} sx       - Screen X coordinate.
     * @param {number} sy       - Screen Y coordinate.
     * @param {boolean} ctrl    - Ctrl key pressed.
     * @param {boolean} shift   - Shift key pressed.
     * @param {boolean} alt     - Alt key pressed.
     * @param {boolean} meta    - Meta key pressed.
     * @returns {Uint8Array} Packet: [opcode, id(4B), btn, cx(2B), cy(2B), px(2B), py(2B), sx(2B), sy(2B), modifiers].
     */
    packets_out[SEND_CLICK] = (id, btn, cx, cy, px, py, sx, sy, ctrl, shift, alt, meta) => {
        const buf = new Uint8Array(20);
        const dv = new DataView(buf.buffer);
        buf[0] = SEND_CLICK;
        dv.setUint32(1, id);
        buf[5] = btn;
        dv.setInt16(6, cx);
        dv.setInt16(8, cy);
        dv.setInt16(10, px);
        dv.setInt16(12, py);
        dv.setInt16(14, sx);
        dv.setInt16(16, sy);
        buf[18] = (ctrl ? 1 : 0) | (shift ? 2 : 0) | (alt ? 4 : 0) | (meta ? 8 : 0);
        return buf;
    };

    /**
     * Serialize a key-up packet.
     * @param {number} id       - Element identifier.
     * @param {string} key      - Key value (e.g., "Enter").
     * @param {boolean} repeat  - Whether key is repeating.
     * @param {boolean} ctrl    - Ctrl key pressed.
     * @param {boolean} shift   - Shift key pressed.
     * @param {boolean} alt     - Alt key pressed.
     * @param {boolean} meta    - Meta key pressed.
     * @returns {Uint8Array} Packet: [opcode, id(4B), repeatFlag, modifiers, keyLengthHigh, keyLengthLow, UTF-8 key bytes...].
     */
    packets_out[SEND_KEY_UP] = (id, key, repeat, ctrl, shift, alt, meta) => {
        const keyBytes = enc.encode(key);
        const len = keyBytes.length;
        const buf = new Uint8Array(9 + len);
        const dv = new DataView(buf.buffer);
        buf[0] = SEND_KEY_UP;
        dv.setUint32(1, id);
        buf[5] = repeat ? 1 : 0;
        buf[6] = (ctrl ? 1 : 0) | (shift ? 2 : 0) | (alt ? 4 : 0) | (meta ? 8 : 0);
        buf[7] = (len >> 8) & 0xFF;
        buf[8] = len & 0xFF;
        buf.set(keyBytes, 9);
        return buf;
    };

    /** Serialize a key-down packet (identical to key-up). */
    packets_out[SEND_KEY_DOWN] = (id, key, repeat, ctrl, shift, alt, meta) => {
        const keyBytes = enc.encode(key);
        const len = keyBytes.length;
        const buf = new Uint8Array(9 + len);
        const dv = new DataView(buf.buffer);
        buf[0] = SEND_KEY_DOWN;
        dv.setUint32(1, id);
        buf[5] = repeat ? 1 : 0;
        buf[6] = (ctrl ? 1 : 0) | (shift ? 2 : 0) | (alt ? 4 : 0) | (meta ? 8 : 0);
        buf[7] = (len >> 8) & 0xFF;
        buf[8] = len & 0xFF;
        buf.set(keyBytes, 9);
        return buf;
    };
    /**
     * Serialize an input/change-value packet.
     * @param {number} id       - Element identifier.
     * @param {string} value    - New input value.
     * @returns {Uint8Array} Packet: [opcode, id(4B), valueLenHigh, valueLenLow, UTF-8 value bytes...].
     */
    packets_out[SEND_VALUE_CHANGE] = (id, value) => {
        const valueBytes = enc.encode(value);
        const len = valueBytes.length;
        const buf = new Uint8Array(7 + len);
        const dv = new DataView(buf.buffer);
        buf[0] = SEND_VALUE_CHANGE;
        dv.setUint32(1, id);
        dv.setUint16(5, len);
        buf.set(valueBytes, 7);
        return buf;
    };

    /**
     * Serialize a form-submit packet.
     * @param {number} id - Form element identifier.
     * @returns {Uint8Array} Packet: [opcode, id(4B)].
     */
    packets_out[SEND_SUBMIT] = (id) => {
        const buf = new Uint8Array(5);
        const dv = new DataView(buf.buffer);
        buf[0] = SEND_SUBMIT;
        dv.setUint32(1, id);
        return buf;
    };

    /**
     * Serialize a file-upload packet.
     * @param {number} id            - Input element identifier.
     * @param {string} fileName      - Name of the file.
     * @param {ArrayBuffer} arrayBuffer - File data.
     * @returns {Uint8Array} Packet: [opcode, id(4B), nameLenHigh, nameLenLow, fileLenHigh(4B)..., nameBytes..., fileBytes...].
     */
    packets_out[SEND_FILE] = (id, fileName, arrayBuffer) => {
        const nameBytes = enc.encode(fileName);
        const nameLen = nameBytes.length;
        const fileBytes = new Uint8Array(arrayBuffer);
        const fileLen = fileBytes.length;
        const buf = new Uint8Array(11 + nameLen + fileLen);
        const dv = new DataView(buf.buffer);
        buf[0] = SEND_FILE;
        dv.setUint32(1, id);
        dv.setUint16(5, nameLen);
        dv.setUint32(7, fileLen);
        buf.set(nameBytes, 11);
        buf.set(fileBytes, 11 + nameLen);
        return buf;
    };

    /**
     * Serialize a heartbeat packet.
     * The server only uses this to touch the session and keep the WebSocket active.
     * @returns {Uint8Array} Packet: [opcode].
     */
    packets_out[SEND_HEARTBEAT] = () => new Uint8Array([SEND_HEARTBEAT]);
    /**
     * Handle server‐sent title updates.
     * Packet format: [opcode=RECEIVE_TITLE][lenHigh][lenLow][UTF-8 title…]
     * @param {DataView} view - DataView over the received ArrayBuffer.
     */
    packets_in[RECEIVE_TITLE] = (view) => {
        const len = view.getUint16(1);
        document.title = dec.decode(new Uint8Array(view.buffer, view.byteOffset + 3, len));
    };

    let csrfToken = "";
    let sessionActions = Promise.resolve();
    let cookieRenewAt = Infinity;
    let heartbeatInterval = 25000;
    let renewingCookie = false;
    let nextSessionRequest = 0;
    let pendingSessionAck = null;
    packets_out[11] = request => {
        const bytes = new Uint8Array(5);
        bytes[0] = 11;
        new DataView(bytes.buffer).setInt32(1, request);
        return bytes;
    };
    function rememberSession(state) {
        csrfToken = state.csrfToken;
        if (state.cookieMaxAge) {
            cookieRenewAt = Date.now() + state.cookieMaxAge * 750;
            heartbeatInterval = Math.max(250, Math.min(25000, state.cookieMaxAge * 250));
            if (socketIsOpen(window.domSocket)) startHeartbeat();
        }
    }
    async function sessionState() {
        const response = await fetch("/_geshra/session", {credentials: "same-origin", cache: "no-store"});
        if (!response.ok) throw new Error("Unable to read the browser session");
        const state = await response.json();
        rememberSession(state);
        return state;
    }

    async function sessionRequest(action, fields) {
        if (action === "session") return sessionState();
        if (!csrfToken) await sessionState();
        const authentication = action === "login" || action === "logout";
        const request = authentication ? ++nextSessionRequest : 0;
        const response = await fetch("/_geshra/" + action, {
            method: "POST",
            credentials: "same-origin",
            cache: "no-store",
            headers: {"Content-Type": "application/x-www-form-urlencoded", "X-CSRF-Token": csrfToken},
            body: new URLSearchParams({...fields, _request: request})
        });
        const state = await response.json();
        if (state.csrfToken) rememberSession(state);
        if (authentication && socketIsOpen(window.domSocket)) {
            await new Promise((resolve, reject) => {
                const timeout = setTimeout(() => {
                    if (pendingSessionAck?.request === request) pendingSessionAck = null;
                    reject(new Error("Authentication callback timed out"));
                }, 10000);
                pendingSessionAck = {request, resolve: () => {clearTimeout(timeout); resolve();}};
                sendPacket(11, request);
            });
        }
        if (!response.ok) throw new Error(state.error || "Session action failed");
        if (action === "login") state.success = response.headers.get("X-Authentication-Success") === "true";
        return state;
    }

    function queueSessionAction(action, fields) {
        const result = sessionActions.then(() => sessionRequest(action, fields));
        sessionActions = result.catch(() => {});
        return result;
    }

    window.geshra = Object.freeze({
        session: () => queueSessionAction("session"),
        login: (username, password) => queueSessionAction("login", {username, password}),
        logout: () => queueSessionAction("logout")
    });

    packets_in[6] = view => {
        const length = view.getUint16(1);
        const action = JSON.parse(dec.decode(new Uint8Array(view.buffer, view.byteOffset + 3, length)));
        if (action.action === "ack") {
            if (pendingSessionAck?.request === action.request) { const ack = pendingSessionAck; pendingSessionAck = null; ack.resolve(); }
            return;
        }
        if (action.action === "state") { rememberSession(action); return; }
        queueSessionAction(action.action, action.action === "login" ? {username: action.username, password: action.password} : {})
            .catch(error => console.error("Session action failed:", error.message));
    };

    /**
     * Handle server‐sent cookie instructions.
     * Packet format: [opcode=RECEIVE_COOKIE][paramCount][key][len][value]… repeated
     * @param {DataView} view
     */
    packets_in[RECEIVE_COOKIE] = (view) => {
        let offset = 1;
        const count = view.getUint8(offset++);
        const cookieArr = [];
        for (let i = 0; i < count; i++) {
            const key = view.getUint8(offset++);
            const len = view.getUint16(offset);
            offset += 2;
            const val = dec.decode(new Uint8Array(view.buffer, offset, len));
            offset += len;
            cookieArr[key] = val;
        }
        // Build cookie string: name=value; otherAttrs...
        let str = `${cookieArr[COOKIE_NAME]}=${cookieArr[COOKIE_VALUE]}`;
        if (cookieArr[COOKIE_PATH]) str += `; path=${cookieArr[COOKIE_PATH]}`;
        if (cookieArr[COOKIE_DOMAIN]) str += `; domain=${cookieArr[COOKIE_DOMAIN]}`;
        if (cookieArr[COOKIE_MAX_AGE]) str += `; max-age=${cookieArr[COOKIE_MAX_AGE]}`;
        if (cookieArr[COOKIE_EXPIRES]) str += `; expires=${cookieArr[COOKIE_EXPIRES]}`;
        if (cookieArr[COOKIE_SECURE]) str += "; secure";
        if (cookieArr[COOKIE_SAME_SITE]) str += `; SameSite=${cookieArr[COOKIE_SAME_SITE]}`;
        document.cookie = str;
    };

    /**
     * Handle server‐sent navigation commands.
     * Packet format: [opcode=RECEIVE_NAVIGATE][lenHigh][lenLow][UTF-8 path…]
     * @param {DataView} view
     */
    packets_in[RECEIVE_NAVIGATE] = (view) => {
        const len = view.getUint16(1); // read length of path string from byte offset 1
        window.location.href = dec.decode(new Uint8Array(view.buffer, view.byteOffset + 3, len));
    };

    /**
     * Handle server‐sent DOM mutations.
     * Packet format: [opcode=RECEIVE_DOM_UPDATE][countHigh][countLow]
     *   then repeated per mutation:
     *   [type][id(4B)][paramCount][key][lenHigh][lenLow][UTF-8 val…]…
     * @param {DataView} view
     */
    packets_in[RECEIVE_DOM_UPDATE] = (view) => {
        let offset = 1;
        const count = view.getUint16(offset);
        offset += 2;
        for (let i = 0; i < count; i++) {
            const type = view.getUint8(offset++);
            const id = view.getInt32(offset);
            offset += 4;
            const paramCount = view.getUint8(offset++);
            const params = {};
            for (let j = 0; j < paramCount; j++) {
                const key = view.getUint8(offset++);
                const len = view.getUint16(offset);
                offset += 2;
                params[key] = dec.decode(new Uint8Array(view.buffer, offset, len));
                offset += len;
            }
            updateDOM(type, id, params);
        }
        flushValueSynchronization();

    };


    function createRuntimeElement(tagName, id, parent) {
        const svg = tagName === "svg" || parent?.namespaceURI === "http://www.w3.org/2000/svg" && parent.localName !== "foreignObject";
        const element = svg ? document.createElementNS("http://www.w3.org/2000/svg", tagName) : document.createElement(tagName);
        element.id = id;

        if (String(tagName).toLowerCase() === "script") {
            element.dataset.pmPendingScript = "true";
        }

        return element;
    }

    function setRuntimeText(element, value) {
        if (isScriptElement(element)) {
            activateInlineScript(element, value || "");
            return;
        }

        for (const child of element.children) cleanupRuntimeTree(child);
        element.textContent = value;
        markValueSynchronization(element);
    }

    function setRuntimeHTML(element, value) {
        if (isScriptElement(element)) {
            activateInlineScript(element, value || "");
            return;
        }

        for (const child of element.children) cleanupRuntimeTree(child);
        element.innerHTML = value;
        markValueSynchronization(element);
    }

    function setRuntimeAttribute(element, key, value) {
        if (isScriptElement(element) && String(key).toLowerCase() === "src") {
            activateExternalScript(element, value);
            return;
        }

        element.setAttribute(key, value);
    }

    function isScriptElement(element) {
        return element && element.tagName && element.tagName.toLowerCase() === "script";
    }

    function copyScriptAttributes(from, to, skipSrc) {
        for (const attribute of Array.from(from.attributes || [])) {
            const name = attribute.name.toLowerCase();

            if (name === "data-geshra-pending-script") {
                continue;
            }

            if (skipSrc && name === "src") {
                continue;
            }

            to.setAttribute(attribute.name, attribute.value);
        }
    }

    function configureScriptExecutionMode(placeholder, script) {
        if (placeholder.hasAttribute("async")) {
            script.async = true;
            return;
        }

        if (placeholder.hasAttribute("defer")) {
            script.defer = true;
            script.async = false;
            return;
        }

        script.async = false;
    }

    function notifyRouteScriptLoaded(src, script) {
        document.dispatchEvent(new CustomEvent("geshra:route-script-loaded", {
            detail: {
                src: src || "",
                script: script
            }
        }));
    }

    function activateExternalScript(placeholder, src) {
        if (!src) {
            placeholder.removeAttribute("src");
            return;
        }

        const script = document.createElement("script");
        copyScriptAttributes(placeholder, script, true);
        script.id = placeholder.id;
        configureScriptExecutionMode(placeholder, script);

        script.addEventListener("load", () => {
            notifyRouteScriptLoaded(src, script);
        }, {once: true});

        script.addEventListener("error", () => {
            console.error("Unable to load route script:", src);
        }, {once: true});

        script.src = src;

        if (placeholder.parentNode) {
            placeholder.parentNode.replaceChild(script, placeholder);
        } else {
            placeholder.setAttribute("src", src);
        }
    }

    function activateInlineScript(placeholder, code) {
        const script = document.createElement("script");
        copyScriptAttributes(placeholder, script, true);
        script.id = placeholder.id;
        configureScriptExecutionMode(placeholder, script);
        script.text = code;

        if (placeholder.parentNode) {
            placeholder.parentNode.replaceChild(script, placeholder);
            notifyRouteScriptLoaded("", script);
        } else {
            placeholder.text = code;
        }
    }

    /**
     * Apply a single DOM mutation based on type, element ID, and params.
     * @param {number} type - Mutation opcode (SET_TITLE, SET_TEXT, etc.).
     * @param {number} id   - Element’s DOM id attribute.
     * @param {Object} params - Parameters indexed by constants (TEXT, HTML, KEY, etc.).
     */
    // Browser-owned state is weak except for explicitly retained detached subtrees.
    const nativeListeners = new WeakMap();
    const virtualLists = new WeakMap();
    const valueSnapshots = new WeakMap();
    const pendingValueSync = new Set();
    const detachedRoots = new Map();

    function findRuntimeElement(id) {
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

    function browserValue(element) {
        if (element.type === "checkbox" || element.type === "radio") return String(element.checked);
        if (element.tagName === "SELECT" && element.multiple) {
            let result = "";
            for (let i = 0; i < element.selectedOptions.length; i++) {
                if (i) result += ",";
                result += "v" + encodeURIComponent(element.selectedOptions[i].value);
            }
            return result;
        }
        return element.value == null ? "" : String(element.value);
    }

    function synchronizeRadioPeers(element) {
        if (element.type !== "radio" || !element.name) return;
        for (const peer of element.getRootNode().querySelectorAll('input[type="radio"][name="' + CSS.escape(element.name) + '"]')) {
            if (peer !== element && peer.name === element.name && peer.form === element.form && valueSnapshots.has(peer)) pendingValueSync.add(peer);
        }
    }

    function setBrowserValue(element, value) {
        const text = value == null ? "" : String(value);
        valueSnapshots.set(element, text);
        if (element.type === "checkbox" || element.type === "radio") element.checked = text === "true";
        else if (element.tagName === "SELECT" && element.multiple) {
            const keys = new Set(text ? text.split(",").map(token => decodeURIComponent(token.slice(1))) : []);
            for (const option of element.options) option.selected = keys.has(option.value);
        } else element.value = text;
        pendingValueSync.add(element);
        synchronizeRadioPeers(element);
    }

    function flushValueSynchronization() {
        for (const element of pendingValueSync) {
            if (!valueSnapshots.has(element)) continue;
            const actual = browserValue(element);
            if (actual !== valueSnapshots.get(element)) {
                valueSnapshots.set(element, actual);
                sendPacket(SEND_VALUE_SYNC, Number(element.id), actual);
            }
        }
        pendingValueSync.clear();
    }

    function markValueSynchronization(element) {
        if (valueSnapshots.has(element)) pendingValueSync.add(element);
        const select = element.tagName === "SELECT" ? element : element.closest?.("select");
        if (select && valueSnapshots.has(select)) pendingValueSync.add(select);
    }

    packets_out[SEND_VALUE_SYNC] = packets_out[SEND_VALUE_CHANGE];
    packets_out[SEND_VIEWPORT] = (id, revision, first, end) => {
        const bytes = new Uint8Array(17);
        const view = new DataView(bytes.buffer);
        view.setUint8(0, SEND_VIEWPORT);
        view.setInt32(1, id);
        view.setInt32(5, revision);
        view.setInt32(9, first);
        view.setInt32(13, end);
        return bytes;
    };
    // The common scalar encoder writes its original opcode; patch it for silent synchronization.
    const scalarSyncEncoder = packets_out[SEND_VALUE_SYNC];
    packets_out[SEND_VALUE_SYNC] = (...args) => {
        const bytes = scalarSyncEncoder(...args);
        if (bytes) bytes[0] = SEND_VALUE_SYNC;
        return bytes;
    };
    packets_out[SEND_DOM_EVENT] = (id, name, value) => {
        const event = enc.encode(name);
        const snapshot = enc.encode(value || "");
        if (event.length > 65535 || snapshot.length > 65535) return null;
        const bytes = new Uint8Array(9 + event.length + snapshot.length);
        const view = new DataView(bytes.buffer);
        view.setUint8(0, SEND_DOM_EVENT);
        view.setInt32(1, id);
        view.setUint16(5, event.length);
        bytes.set(event, 7);
        view.setUint16(7 + event.length, snapshot.length);
        bytes.set(snapshot, 9 + event.length);
        return bytes;
    };

    function addRuntimeListener(element, name, options) {
        let bindings = nativeListeners.get(element);
        if (!bindings) nativeListeners.set(element, bindings = new Map());
        let binding = bindings.get(name);
        if (binding) {
            binding.typed ||= !!options.typed;
            binding.generic ||= !!options.generic;
            if (options.generic) binding.preventDefault = !!options.preventDefault;
            return;
        }
        binding = {typed: !!options.typed, generic: !!options.generic, preventDefault: !!options.preventDefault};
        binding.listener = event => {
            const id = Number(element.id);
            if (binding.preventDefault || name === "submit" && binding.typed) event.preventDefault();
            if (binding.typed) {
                switch (name) {
                    case "click": sendPacket(SEND_CLICK, id, event.button, event.clientX, event.clientY, event.pageX, event.pageY, event.screenX, event.screenY, event.ctrlKey, event.shiftKey, event.altKey, event.metaKey); break;
                    case "keyup": sendPacket(SEND_KEY_UP, id, event.key, event.repeat, event.ctrlKey, event.shiftKey, event.altKey, event.metaKey); break;
                    case "keydown": sendPacket(SEND_KEY_DOWN, id, event.key, event.repeat, event.ctrlKey, event.shiftKey, event.altKey, event.metaKey); break;
                    case "input": {
                        const value = browserValue(element);
                        valueSnapshots.set(element, value);
                        sendPacket(SEND_VALUE_CHANGE, id, value);
                        synchronizeRadioPeers(element);
                        flushValueSynchronization();
                        break;
                    }
                    case "submit": sendPacket(SEND_SUBMIT, id); break;
                    case "change": {
                        if (element.type === "file") for (const file of element.files) {
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
                if (element.tagName === "DIALOG" && name === "close") value = element.returnValue;
                else if (name === "toggle") value = event.newState || (element.open ? "open" : "closed");
                sendPacket(SEND_DOM_EVENT, id, name, value);
            }
        };
        bindings.set(name, binding);
        element.addEventListener(name, binding.listener, {passive: false});
        if (options.typed && name === "input") {
            valueSnapshots.set(element, browserValue(element));
            pendingValueSync.add(element);
        }
    }

    function removeRuntimeListener(element, name, mode) {
        const bindings = nativeListeners.get(element);
        const binding = bindings?.get(name);
        if (!binding) return;
        if (mode === "generic") { binding.generic = false; binding.preventDefault = false; }
        else binding.typed = false;
        if (!binding.typed && !binding.generic) {
            element.removeEventListener(name, binding.listener);
            bindings.delete(name);
        }
    }

    function cleanupRuntimeTree(element, retained = false) {
        const walker = document.createTreeWalker(element, NodeFilter.SHOW_ELEMENT);
        let node = element;
        do {
            const state = virtualLists.get(node);
            if (state) {
                if (state.raf) cancelAnimationFrame(state.raf);
                state.raf = 0;
                state.observer.disconnect();
                if (!retained) {
                    node.removeEventListener("scroll", state.scroll);
                    virtualLists.delete(node);
                }
            }
            pendingValueSync.delete(node);
            if (!retained) {
                const bindings = nativeListeners.get(node);
                if (bindings) for (const [name, binding] of bindings) node.removeEventListener(name, binding.listener);
                nativeListeners.delete(node);
                valueSnapshots.delete(node);
                detachedRoots.delete(String(node.id));
            }
        } while ((node = walker.nextNode()));
    }

    function resolveBrowserArguments(value) {
        if (Array.isArray(value)) return value.map(resolveBrowserArguments);
        if (value && typeof value === "object") {
            if (Object.keys(value).length === 1 && "$element" in value) return findRuntimeElement(value.$element);
            for (const key of Object.keys(value)) value[key] = resolveBrowserArguments(value[key]);
        }
        return value;
    }

    function updateVirtualPosition(state) {
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

    function configureVirtualList(element, configuration) {
        let state = virtualLists.get(element);
        if (!state) {
            state = {element, first: 0, raf: 0};
            state.schedule = () => {
                if (!state.raf) state.raf = requestAnimationFrame(() => {state.raf = 0; requestVirtualWindow(state);});
            };
            state.scroll = state.schedule;
            state.observer = new ResizeObserver(state.schedule);
            element.addEventListener("scroll", state.scroll, {passive: true});
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
        element.fsiScrollToIndex = index => {
            const height = element.clientHeight;
            const logical = state.count * state.rowHeight;
            const physical = Math.min(logical, 8000000);
            const ratio = logical > height && physical > height ? (logical - height) / (physical - height) : 1;
            element.scrollTop = index * state.rowHeight / ratio;
            state.schedule();
        };
    }

    function updateDOM(type, id, params) {
        const element = findRuntimeElement(id);

        if (!element) return;

        switch (type) {
            case 35: {
                const metadata = JSON.parse(params[VALUE]);
                for (const node of document.head.querySelectorAll("[data-geshra-seo]")) node.remove();
                document.title = metadata.title;
                if (metadata.head) {
                    for (const title of document.head.querySelectorAll("title")) title.remove();
                    document.head.insertAdjacentHTML("beforeend", metadata.head);
                }
                document.documentElement.lang = metadata.language;
                if (metadata.noIndex) {
                    const robots = document.createElement("meta");
                    robots.dataset.fsiSeo = "";
                    robots.name = "robots";
                    robots.content = "noindex,nofollow";
                    document.head.appendChild(robots);
                }
                break;
            }
            case 25: {
                element[params[KEY]] = resolveBrowserArguments(JSON.parse(params[VALUE]));
                markValueSynchronization(element);
                synchronizeRadioPeers(element);
                break;
            }
            case 26: {
                const args = resolveBrowserArguments(JSON.parse(params[VALUE]));
                try {
                    const result = element[params[KEY]](...args);
                    if (result && typeof result.catch === "function") result.catch(error => console.error("Native method failed", error));
                } catch (error) { console.error("Native method failed", error); }
                break;
            }
            case 27: configureVirtualList(element, JSON.parse(params[VALUE])); break;
            case 28: {
                const window = JSON.parse(params[VALUE]);
                const state = virtualLists.get(element);
                if (state && state.revision === window.revision) {
                    state.first = window.first;
                    updateVirtualPosition(state);
                }
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
                    if (state) {state.observer.observe(node); state.schedule();}
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
                    const context = element.getContext("2d");
                    const value = resolveBrowserArguments(JSON.parse(params[VALUE]));
                    if (type === 32) context[params[KEY]](...value);
                    else context[params[KEY]] = value;
                } catch (error) { console.error("Canvas command failed", error); }
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
                markValueSynchronization(element);
                synchronizeRadioPeers(element);
                break;
            case SET_PROPERTY:
                const prop = params[PROPERTY];
                const value = params[VALUE];

                if (BOOLEAN_PROPERTIES.has(prop)) {
                    element[prop] = value === "true";
                } else {
                    element[prop] = value;
                }
                markValueSynchronization(element);
                synchronizeRadioPeers(element);
                break;
            case SET_CLASS:
                element.setAttribute("class", params[CLASS_NAME]);
                break;
            case SET_STYLE:
                element.style.setProperty(params[STYLE_PROP], params[STYLE_VAL]);
                break;
            case 34:
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
                markValueSynchronization(element.parentElement || element);
                cleanupRuntimeTree(element);
                element.remove();
                break;
            case CLEAR_CHILDREN:
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
     * How often the browser sends a small heartbeat packet while the page is open.
     * This keeps the server-side SessionContext from expiring while the user is simply reading the page.
     */
    const HEARTBEAT_INTERVAL_MS = 25_000;

    /**
     * Reconnect delay bounds for a dropped WebSocket.
     */
    const RECONNECT_BASE_DELAY_MS = 500;
    const RECONNECT_MAX_DELAY_MS = 10_000;

    /**
     * If the page has been disconnected for this long, server-side component IDs may be stale.
     * In that case the runtime drops queued UI events and reloads the current route instead.
     */
    const STALE_RECONNECT_MS = 25 * 60_000;

    /**
     * Maximum number of user packets buffered while the socket is reconnecting.
     */
    const MAX_QUEUED_PACKETS = 128;

    let reconnectTimer = null;
    let heartbeatTimer = null;
    let reconnectAttempt = 0;
    let connecting = false;
    let firstConnection = true;
    let socketClosedAt = 0;
    const queuedPackets = [];

    function socketIsOpen(socket) {
        return socket && socket.readyState === WebSocket.OPEN;
    }

    function socketIsConnecting(socket) {
        return socket && socket.readyState === WebSocket.CONNECTING;
    }

    function socketIsClosing(socket) {
        return socket && socket.readyState === WebSocket.CLOSING;
    }

    function createPacket(op, args) {
        const fn = packets_out[op];
        return fn ? fn(...args) : null;
    }

    function queuePacket(packet) {
        if (!packet) {
            return;
        }

        queuedPackets.push(packet);

        while (queuedPackets.length > MAX_QUEUED_PACKETS) {
            queuedPackets.shift();
        }
    }

    function flushQueuedPackets() {
        const socket = window.domSocket;

        while (socketIsOpen(socket) && queuedPackets.length > 0) {
            socket.send(queuedPackets.shift());
        }
    }

    function clearReconnectTimer() {
        if (reconnectTimer !== null) {
            clearTimeout(reconnectTimer);
            reconnectTimer = null;
        }
    }

    function stopHeartbeat() {
        if (heartbeatTimer !== null) {
            clearInterval(heartbeatTimer);
            heartbeatTimer = null;
        }
    }

    function startHeartbeat() {
        stopHeartbeat();

        heartbeatTimer = setInterval(() => {
            sendPacket(SEND_HEARTBEAT);
            if (Date.now() >= cookieRenewAt && !renewingCookie) {
                renewingCookie = true;
                queueSessionAction("session")
                    .catch(error => console.error("Session renewal failed:", error.message))
                    .finally(() => { renewingCookie = false; });
            }
        }, heartbeatInterval);
    }

    function scheduleReconnect(delayOverride) {
        const socket = window.domSocket;

        if (socketIsOpen(socket) || socketIsConnecting(socket) || connecting || reconnectTimer !== null) {
            return;
        }

        if (socketIsClosing(socket) && delayOverride === undefined) {
            reconnectTimer = setTimeout(() => {
                reconnectTimer = null;
                scheduleReconnect(0);
            }, 300);
            return;
        }

        const delay = delayOverride !== undefined
            ? delayOverride
            : Math.min(RECONNECT_MAX_DELAY_MS, RECONNECT_BASE_DELAY_MS * Math.pow(2, reconnectAttempt++));

        reconnectTimer = setTimeout(() => {
            reconnectTimer = null;
            connectSocket();
        }, delay);
    }

    function connectSocket() {
        const existing = window.domSocket;

        if (socketIsOpen(existing) || socketIsConnecting(existing) || connecting) {
            return;
        }

        if (socketIsClosing(existing)) {
            scheduleReconnect(300);
            return;
        }

        connecting = true;
        clearReconnectTimer();

        const socketProtocol = location.protocol === "https:" ? "wss:" : "ws:";
        const socket = new WebSocket(`${socketProtocol}//${location.host}/ws`);
        socket.binaryType = "arraybuffer";
        window.domSocket = socket;

        socket.onopen = () => {
            const staleReconnect = !firstConnection && socketClosedAt > 0 && Date.now() - socketClosedAt > STALE_RECONNECT_MS;

            connecting = false;
            reconnectAttempt = 0;
            socketClosedAt = 0;
            clearReconnectTimer();
            startHeartbeat();

            if (firstConnection || staleReconnect) {
                firstConnection = false;
                queuedPackets.length = 0;
                sendPacket(SEND_NAVIGATE, location.pathname);
                return;
            }

            sendPacket(SEND_HEARTBEAT);
            flushQueuedPackets();
        };

        socket.onmessage = (event) => {
            const view = new DataView(event.data);
            const opcode = view.getUint8(0);
            const handler = packets_in[opcode];

            if (handler) {
                handler(view);
            } else {
                console.warn("Unknown server packet opcode:", opcode);
            }
        };

        socket.onclose = () => {
            if (window.domSocket === socket) {
                socketClosedAt = Date.now();
            }

            connecting = false;
            stopHeartbeat();
            scheduleReconnect();
        };

        socket.onerror = () => {
            if (window.domSocket === socket && socketClosedAt === 0) {
                socketClosedAt = Date.now();
            }

            connecting = false;
            stopHeartbeat();
            scheduleReconnect(500);
        };
    }

    function ensureSocket() {
        const socket = window.domSocket;

        if (socketIsOpen(socket)) {
            return true;
        }

        if (!socketIsConnecting(socket)) {
            connectSocket();
        }

        return false;
    }

    /**
     * Send a serialized packet to the server.
     * If the WebSocket has gone idle/closed, queue the user action and reconnect.
     * @param {number} op       - Opcode of the packet to send (e.g., SEND_CLICK).
     * @param {...*} args       - Arguments for the corresponding packets_out[op] serializer.
     */
    function sendPacket(op, ...args) {
        const packet = createPacket(op, args);

        if (!packet) {
            return false;
        }

        const socket = window.domSocket;
        if (socketIsOpen(socket)) {
            try {
                socket.send(packet);
                return true;
            } catch (ignored) {
                if (window.domSocket === socket && socketClosedAt === 0) {
                    socketClosedAt = Date.now();
                }
            }
        }

        if (op !== SEND_HEARTBEAT) {
            queuePacket(packet);
        }

        ensureSocket();
        return false;
    }

    /**
     * Set up the WebSocket connection and global event hooks once the DOM is ready.
     */
    window.addEventListener("DOMContentLoaded", () => {
        connectSocket();

        /**
         * Intercept in-page link clicks to drive client-side routing.
         * Only handle same-origin <a> tags.
         */
        document.body.addEventListener("click", (e) => {
            const link = e.target.closest("a[href]");
            if (link && link.origin === location.origin) {
                e.preventDefault();
                history.pushState({}, "", link.pathname);
                sendPacket(SEND_NAVIGATE, link.pathname);
            }
        });

        /**
         * Listen for browser back/forward navigation and notify the server.
         */
        window.addEventListener("popstate", () => {
            sendPacket(SEND_NAVIGATE, location.pathname);
        });

        /**
         * When a sleeping tab wakes up, immediately reconnect/touch the session.
         */
        window.addEventListener("focus", () => {
            if (ensureSocket()) {
                sendPacket(SEND_HEARTBEAT);
            }
        });

        document.addEventListener("visibilitychange", () => {
            if (!document.hidden && ensureSocket()) {
                sendPacket(SEND_HEARTBEAT);
            }
        });

        window.addEventListener("pageshow", () => {
            if (ensureSocket()) {
                sendPacket(SEND_HEARTBEAT);
            }
        });
    });
})();
