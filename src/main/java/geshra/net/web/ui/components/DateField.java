package geshra.net.web.ui.components;

/**
 * Native date input with browser-owned editing, validation, and value sanitization.
 * Programmatic values follow JavaScript and do not fire input or change callbacks.
 */
public class DateField extends NativeTextField {
    /**
     * Creates an empty native date field.
     */
    public DateField() { super("date"); }

    /**
     * Creates a native date field with its initial value.
     * @param value initial browser value
     */
    public DateField(String value) { super("date", value); }
}
