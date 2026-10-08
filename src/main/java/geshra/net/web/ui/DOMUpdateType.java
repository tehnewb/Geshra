package geshra.net.web.ui;

/**
 * Enum representing different types of updates that can be applied to a DOM element.
 * It provides a lightweight encoding mechanism using integer codes for efficient processing.
 * Each constant corresponds to a specific type of update or action that modifies the DOM.
 * <p>
 * The main use of this enum is in encoding operations related to DOM modifications,
 * such as creating elements, updating attributes, managing events, and handling structural changes.
 */
public enum DOMUpdateType {

    /**
     * Sets the document title.
     */
    TITLE(0),

    // Basic content and property updates
    /**
     * Sets the textContent of an element.
     */
    SET_TEXT(1),
    /**
     * Sets the innerHTML of an element.
     */
    SET_HTML(2),
    /**
     * Sets a specific attribute of an element.
     */
    SET_ATTRIBUTE(3),
    /**
     * Sets a DOM property for an element.
     */
    SET_PROPERTY(4),
    /**
     * Sets the className of an element.
     */
    SET_CLASS(5),
    /**
     * Sets an inline CSS style for an element.
     */
    SET_STYLE(6),
    /**
     * Sets the value property for input-like elements.
     */
    SET_VALUE(7),

    // DOM manipulation
    /**
     * Appends a child element to the current element.
     */
    APPEND_CHILD(8, 0),
    /**
     * Removes the current element.
     */
    REMOVE(9, 0),
    /**
     * Inserts an element before the current element.
     */
    INSERT_BEFORE(10, 0),
    /**
     * Inserts an element after the current element.
     */
    INSERT_AFTER(11, 0),
    /**
     * Replaces the current element with another element.
     */
    REPLACE(12, 0),
    /**
     * Removes all children of the current element (e.g. innerHTML = "").
     */
    CLEAR_CHILDREN(13, 0),

    // Events
    /**
     * Adds an event listener to an element.
     */
    ADD_EVENT_LISTENER(14),
    /**
     * Removes an event listener from an element (future support).
     */
    REMOVE_EVENT_LISTENER(15),
    /**
     * Triggers a custom event on an element.
     */
    TRIGGER_EVENT(16),

    // Advanced
    /**
     * Toggles a class value in the classList of an element.
     */
    TOGGLE_CLASS(17),
    /**
     * Sets a data-* attribute on an element.
     */
    SET_DATASET(18),
    /**
     * Focuses the current element.
     */
    FOCUS(19),
    /**
     * Removes focus from the current element.
     */
    BLUR(20),
    /**
     * Scrolls to the specified position in an element.
     */
    SCROLL_TO(21),
    /**
     * Adds a new class name to the classList of an element.
     */
    ADD_CLASS(22),
    /**
     * Removes a class name from the classList of an element.
     */
    REMOVE_CLASS(23),
    /**
     * Sets the type attribute of an element (e.g., input type).
     */
    SET_TYPE(24),
    /**
     * Assigns a JSON-typed native DOM property.
     */
    SET_TYPED_PROPERTY(25),
    /**
     * Calls a native element method with JSON arguments.
     */
    CALL_METHOD(26, Integer.MAX_VALUE),
    /**
     * Configures a bounded virtual list.
     */
    VIRTUAL_CONFIG(27),
    /**
     * Acknowledges the rendered virtual item window.
     */
    VIRTUAL_WINDOW(28, Integer.MAX_VALUE),
    /**
     * Detaches a retained component subtree.
     */
    DETACH(29, 0),
    /**
     * Moves an existing component without recreating it.
     */
    MOVE_CHILD(30, 0),
    /**
     * Removes a DOM attribute rather than assigning a string value.
     */
    REMOVE_ATTRIBUTE(31),
    /**
     * Calls a native canvas 2D context method.
     */
    CANVAS_METHOD(32, Integer.MAX_VALUE),
    /**
     * Assigns a native canvas 2D context property.
     */
    CANVAS_PROPERTY(33),
    /**
     * Applies an initial value after descendants receive their option properties.
     */
    INITIAL_VALUE(34, Integer.MAX_VALUE),
    /**
     * Replaces managed document metadata after navigation.
     */
    SET_SEO(35);

    /**
     * Explicit wire-code index built once, avoiding per-lookup enum-array cloning.
     */
    private static final DOMUpdateType[] BY_CODE = buildCodeIndex();

    private final byte code; // The integer-based code representing the update type. This enables efficient encoding and decoding.
    private final int defaultPriority; // Base ordering priority used before enqueue order is applied.

    /**
     * Constructor to initialize the DOM update type with its corresponding code.
     *
     * @param code an integer code representing the DOM update type
     */
    DOMUpdateType(int code, int defaultPriority) {
        this.code = (byte) code;
        this.defaultPriority = defaultPriority;
    }

    /**
     * Associates this constant with its compact browser protocol identifier.
     *
     * @param code compact protocol identifier
     */
    DOMUpdateType(int code) {
        this(code, Integer.MAX_VALUE - 1);
    }

    /**
     * Retrieves the corresponding DOMUpdateType for a specified integer code.
     *
     * @param code the integer code of the desired DOMUpdateType
     * @return the matching DOMUpdateType constant
     * @throws IllegalArgumentException if no matching DOMUpdateType is found
     */
    public static DOMUpdateType fromCode(int code) {
        /*
         * Protocol codes index the precomputed table directly; enum ordinals are not routing IDs.
         */
        if (code >= 0 && code < BY_CODE.length) {
            DOMUpdateType value = BY_CODE[code];
            if (value != null) return value;
        }
        throw new IllegalArgumentException("Unknown DOMUpdateType code: " + code);
    }

    /**
     * Builds a dense lookup from the explicitly assigned wire codes.
     * @return immutable-after-initialization code index
     */
    private static DOMUpdateType[] buildCodeIndex() {
        /*
         * Scan once during class initialization, retaining explicit protocol IDs if declarations move.
         */
        DOMUpdateType[] values = values();
        int highest = 0;
        for (DOMUpdateType value : values) highest = Math.max(highest, value.code);
        DOMUpdateType[] index = new DOMUpdateType[highest + 1];
        for (DOMUpdateType value : values) index[value.code] = value;
        return index;
    }

    /**
     * Gets the integer code of the current DOMUpdateType.
     *
     * @return the code representing this DOM update type
     */
    public int getCode() {
        return code;
    }

    /**
     * Returns the base ordering priority; structural updates run before property updates.
     * @return the resulting default priority value
     */
    public int getDefaultPriority() {
        return defaultPriority;
    }
}
