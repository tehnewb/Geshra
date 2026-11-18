/**
 * Outgoing packet opcodes.
 * These codes identify the type of user action sent from client → server.
 */
export const SEND_NAVIGATE = 0;
/**
 * Send mouse click details (button, coords, modifiers)
 */
export const SEND_CLICK = 1;
/**
 * Send key-up event (key, repeat, modifiers)
 */
export const SEND_KEY_UP = 2;
/**
 * Send key-down event (key, repeat, modifiers)
 */
export const SEND_KEY_DOWN = 3;
/**
 * Send input/change value (e.g., text, checkbox)
 */
export const SEND_VALUE_CHANGE = 4;
/**
 * Send form submit event
 */
export const SEND_SUBMIT = 5;
/**
 * Send file contents (filename + binary data)
 */
export const SEND_FILE = 6;
/**
 * Client viewport action identifier.
 */
export const SEND_VIEWPORT = 8;
/**
 * Client native-event action identifier.
 */
export const SEND_DOM_EVENT = 9;
/**
 * Client silent-value action identifier.
 */
export const SEND_VALUE_SYNC = 10;
/**
 * Keep the WebSocket/session alive while the page is idle
 */
export const SEND_HEARTBEAT = 7;

/**
 * Incoming packet opcodes.
 * These codes identify the type of update sent from server → client.
 */
export const RECEIVE_NAVIGATE = 0;
/**
 * Apply one or more dynamic DOM mutations
 */
export const RECEIVE_DOM_UPDATE = 1;
/**
 * Set or update document.cookie
 */
export const RECEIVE_COOKIE = 2;
/**
 * Update document.title
 */
export const RECEIVE_TITLE = 3;

/**
 * Replace the document’s title
 */
export const SET_TITLE = 0;
/**
 * Update an element’s textContent
 */
export const SET_TEXT = 1;
/**
 * Update an element’s innerHTML
 */
export const SET_HTML = 2;
/**
 * Set an attribute on an element
 */
export const SET_ATTRIBUTE = 3;
/**
 * Set a property on an element (e.g., checked, value)
 */
export const SET_PROPERTY = 4;
/**
 * Toggle an element’s className
 */
export const SET_CLASS = 5;
/**
 * Set an inline style property (name:value)
 */
export const SET_STYLE = 6;
/**
 * Update an element’s value (e.g., input value)
 */
export const SET_VALUE = 7;
/**
 * Append a newly created child node
 */
export const APPEND_CHILD = 8;
/**
 * Remove an existing node
 */
export const REMOVE = 9;
/**
 * Insert a node before another
 */
export const INSERT_BEFORE = 10;
/**
 * Insert a node after another
 */
export const INSERT_AFTER = 11;
/**
 * Replace one node with another
 */
export const REPLACE = 12;
/**
 * Remove all children from an element
 */
export const CLEAR_CHILDREN = 13;
/**
 * Add an event listener to an element
 */
export const ADD_EVENT = 14;
/**
 * Remove an event listener from an element
 */
export const REMOVE_EVENT = 15;
/**
 * Programmatically trigger an event on an element
 */
export const TRIGGER_EVENT = 16;
/**
 * Toggle a specific class on an element
 */
export const TOGGLE_CLASS = 17;
/**
 * Set a data-* attribute key
 */
export const SET_DATASET = 18;
/**
 * Focus an element
 */
export const FOCUS = 19;
/**
 * Blur (unfocus) an element
 */
export const BLUR = 20;
/**
 * Scroll an element into view (parameters: top, left, behavior)
 */
export const SCROLL_TO = 21;
/**
 * Add a class to an element’s classList
 */
export const ADD_CLASS = 22;
/**
 * Remove a class from an element’s classList
 */
export const REMOVE_CLASS = 23;
/**
 * Change an element’s type property (e.g., input type)
 */
export const SET_TYPE = 24;

/**
 * params[0] holds text values for mutations
 */
export const TEXT = 0;
/**
 * params[1] holds HTML strings for mutations
 */
export const HTML = 1;
/**
 * params[2] holds attribute/property keys
 */
export const KEY = 2;
/**
 * params[3] holds attribute/property values
 */
export const VALUE = 3;
/**
 * params[4] holds property names (for SET_PROPERTY)
 */
export const PROPERTY = 4;
/**
 * params[5] holds class names (for SET_CLASS, ADD_CLASS, REMOVE_CLASS)
 */
export const CLASS_NAME = 5;
/**
 * params[6] holds style property names
 */
export const STYLE_PROP = 6;
/**
 * params[7] holds style values
 */
export const STYLE_VAL = 7;
/**
 * params[8] holds event names (for ADD_EVENT, REMOVE_EVENT, TRIGGER_EVENT)
 */
export const EVENT_NAME = 8;
/**
 * params[9] holds boolean toggle flags
 */
export const CLASS_TOGGLE = 9;
/**
 * params[10] holds boolean “force” flags (for classList methods)
 */
export const FORCE = 10;
/**
 * params[11] holds data-* attribute keys
 */
export const DATASET_KEY = 11;
/**
 * params[12] holds data-* attribute values
 */
export const DATASET_VAL = 12;
/**
 * params[13] holds scroll-to “top” coordinate
 */
export const TOP = 13;
/**
 * params[14] holds scroll-to “left” coordinate
 */
export const LEFT = 14;
/**
 * params[15] holds scroll behavior (e.g., "smooth" or "auto")
 */
export const BEHAVIOR = 15;
/**
 * params[16] holds element identifiers (IDs) for certain ops
 */
export const IDENTIFIER = 16;
/**
 * params[17] holds generic “type” values
 */
export const TYPE = 17;
/**
 * params[18] holds the ID of the child to insert an element into *
 */
export const INSERT_ID = 18;

/**
 * cookieParams[0] = name
 */
export const COOKIE_NAME = 0;
/**
 * cookieParams[1] = value
 */
export const COOKIE_VALUE = 1;
/**
 * cookieParams[2] = path
 */
export const COOKIE_PATH = 2;
/**
 * cookieParams[3] = max-age (in seconds)
 */
export const COOKIE_MAX_AGE = 3;
/**
 * cookieParams[4] = expires (Date string)
 */
export const COOKIE_EXPIRES = 4;
/**
 * cookieParams[5] = domain
 */
export const COOKIE_DOMAIN = 5;
/**
 * cookieParams[6] = secure flag
 */
export const COOKIE_SECURE = 6;
/**
 * cookieParams[7] = HttpOnly flag
 */
export const COOKIE_HTTP_ONLY = 7;
/**
 * cookieParams[8] = SameSite policy ("Strict", "Lax", "None")
 */
export const COOKIE_SAME_SITE = 8;

/**
 * Set of boolean HTML attributes (no string value)
 */
export const BOOLEAN_PROPERTIES = new Set(['checked', 'disabled', 'selected', 'readonly', 'required', 'autofocus', 'multiple']);
