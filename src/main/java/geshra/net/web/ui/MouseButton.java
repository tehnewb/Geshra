package geshra.net.web.ui;

/**
 * Represents the mouse button used in a click event.
 * Mirrors standard browser button values from JavaScript's {@code MouseEvent.button}:
 * <ul>
 *     <li>{@code LEFT} = 0</li>
 *     <li>{@code MIDDLE} = 1</li>
 *     <li>{@code RIGHT} = 2</li>
 *     <li>{@code BACK} = 3 (typically browser back button)</li>
 *     <li>{@code FORWARD} = 4 (typically browser forward button)</li>
 * </ul>
 *
 * <p>Use {@link #fromCode(int)} to convert the raw browser button code into a corresponding enum value.
 *
 * @author Albert
 * @since April 19, 2025
 */
public enum MouseButton {

    /**
     * Browser mouse button for left.
     */
    LEFT(0),
    /**
     * Browser mouse button for middle.
     */
    MIDDLE(1),
    /**
     * Browser mouse button for right.
     */
    RIGHT(2),
    /**
     * Browser mouse button for back.
     */
    BACK(3),
    /**
     * Browser mouse button for forward.
     */
    FORWARD(4);

    /**
     * Explicit wire-code index built once, avoiding per-lookup enum-array cloning.
     */
    private static final MouseButton[] BY_CODE = buildCodeIndex();

    private final int code; // Compact protocol identifier for this value.

    /**
     * Associates this constant with its compact browser protocol identifier.
     *
     * @param code compact protocol identifier
     */
    MouseButton(int code) {
        this.code = code;
    }

    /**
     * Gets the numeric browser-compatible code of this button.
     *
     * @return the raw code (0 for left, 1 for middle, etc.)
     */
    public int getCode() {
        return code;
    }

    /**
     * Converts a raw integer button code into a {@code MouseButton} enum.
     *
     * @param code the button code (0–4)
     * @return the corresponding {@code MouseButton}, or {@code null} if unknown
     */
    public static MouseButton fromCode(int code) {
        /*
         * Protocol codes index the precomputed table directly; enum ordinals are not routing IDs.
         */
        if (code >= 0 && code < BY_CODE.length) {
            MouseButton value = BY_CODE[code];
            if (value != null) return value;
        }
        return null;
    }

    /**
     * Builds a dense lookup from the explicitly assigned wire codes.
     * @return immutable-after-initialization code index
     */
    private static MouseButton[] buildCodeIndex() {
        /*
         * Scan once during class initialization, retaining explicit protocol IDs if declarations move.
         */
        MouseButton[] values = values();
        int highest = 0;
        for (MouseButton value : values) highest = Math.max(highest, value.code);
        MouseButton[] index = new MouseButton[highest + 1];
        for (MouseButton value : values) index[value.code] = value;
        return index;
    }
}
