package geshra.net.web.ui.components;

/**
 * Native time input with browser-owned editing, validation, and value sanitization.
 * Programmatic values follow JavaScript and do not fire input or change callbacks.
 */
public class TimeField extends NativeTextField {
    /**
     * Creates an empty native time field.
     */
    public TimeField() { super("time"); }

    /**
     * Creates a native time field with its initial value.
     * @param value initial browser value
     */
    public TimeField(String value) { super("time", value); }
}
