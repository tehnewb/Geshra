package geshra.net.web.ui;

/**
 * Enumerates the various parameters used to update DOM elements in a
 * compact and efficient manner. Each enum value represents a distinct type
 * of DOM update operation or attribute that can be modified.
 * <p>
 * The associated integer ID for each enum value allows these parameters
 * to be encoded for efficient transmission or used in mapping operations.
 */
public enum DOMUpdateParam {

    /**
     * Text selection or protocol value.
     */
    TEXT(0),
    /**
     * el.innerHTML.
     */
    HTML(1),
    /**
     * el.setAttribute(key, value).
     */
    KEY(2),
    /**
     * el.value = value.
     */
    VALUE(3),
    /**
     * el[property] = value.
     */
    PROPERTY(4),
    /**
     * el.className = value.
     */
    CLASS_NAME(5),
    /**
     * el.style[property] = value.
     */
    STYLE_PROPERTY(6),
    /**
     * value of style[property].
     */
    STYLE_VALUE(7),
    /**
     * event type name (click, input, etc.).
     */
    EVENT_NAME(8),
    /**
     * class name to toggle.
     */
    CLASS_TOGGLE(9),
    /**
     * force true/false toggle.
     */
    FORCE(10),
    /**
     * data-* key.
     */
    DATASET_KEY(11),
    /**
     * value for dataset key.
     */
    DATASET_VALUE(12),
    /**
     * scroll position.
     */
    SCROLL_TOP(13),
    /**
     * scroll position.
     */
    SCROLL_LEFT(14),
    /**
     * smooth / auto.
     */
    SCROLL_BEHAVIOR(15),
    /**
     * Identifier selection or protocol value.
     */
    IDENTIFIER(16),
    /**
     * used for setting the element type update.
     */
    TYPE(17),
    /**
     * the id of the chlid to insert a new element into.
     */
    INSERT_ID(18);
    /**
     * Explicit wire-code index built once, avoiding per-lookup enum-array cloning.
     */
    private static final DOMUpdateParam[] BY_CODE = buildCodeIndex();

    private final byte code; // Compact protocol identifier for this value.

    /**
     * Associates this constant with its compact browser protocol identifier.
     *
     * @param code compact protocol identifier
     */
    DOMUpdateParam(int code) {
        this.code = (byte) code;
    }

    /**
     * Resolves a DOM parameter identifier; unknown identifiers are rejected with IllegalArgumentException.
     *
     * @param id id supplied to this operation
     * @return the resulting from id value
     */
    public static DOMUpdateParam fromID(int id) {
        /*
         * Protocol codes index the precomputed table directly; enum ordinals are not routing IDs.
         */
        if (id >= 0 && id < BY_CODE.length) {
            DOMUpdateParam value = BY_CODE[id];
            if (value != null) return value;
        }
        throw new IllegalArgumentException("Unknown DOMUpdateParam id: " + id);
    }

    /**
     * Builds a dense lookup from the explicitly assigned wire codes.
     * @return immutable-after-initialization code index
     */
    private static DOMUpdateParam[] buildCodeIndex() {
        /*
         * Scan once during class initialization, retaining explicit protocol IDs if declarations move.
         */
        DOMUpdateParam[] values = values();
        int highest = 0;
        for (DOMUpdateParam value : values) highest = Math.max(highest, value.code);
        DOMUpdateParam[] index = new DOMUpdateParam[highest + 1];
        for (DOMUpdateParam value : values) index[value.code] = value;
        return index;
    }

    /**
     * Returns the code retained by this instance.
     * @return the resulting code value
     */
    public int getCode() {
        return code;
    }
}
