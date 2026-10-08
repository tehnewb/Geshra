package geshra.net.web.ui.components;

/**
 * Native datetime-local input with browser-owned editing, validation, and value sanitization.
 * Programmatic values follow JavaScript and do not fire input or change callbacks.
 */
public class DateTimeField extends NativeTextField {
    /**
     * Creates an empty native datetime-local field.
     */
    public DateTimeField() { super("datetime-local"); }

    /**
     * Creates a native datetime-local field with its initial value.
     * @param value initial browser value
     */
    public DateTimeField(String value) { super("datetime-local", value); }
}
