package geshra.net.web.ui.components;

/**
 * Native month input with browser-owned editing, validation, and value sanitization.
 * Programmatic values follow JavaScript and do not fire input or change callbacks.
 */
public class MonthField extends NativeTextField {
    /**
     * Creates an empty native month field.
     */
    public MonthField() { super("month"); }

    /**
     * Creates a native month field with its initial value.
     * @param value initial browser value
     */
    public MonthField(String value) { super("month", value); }
}
