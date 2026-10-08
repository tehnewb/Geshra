package geshra.net.web.ui.components;

/**
 * Native tel input with browser-owned editing, validation, and value sanitization.
 * Programmatic values follow JavaScript and do not fire input or change callbacks.
 */
public class TelephoneField extends NativeTextField {
    /**
     * Creates an empty native tel field.
     */
    public TelephoneField() { super("tel"); }

    /**
     * Creates a native tel field with its initial value.
     * @param value initial browser value
     */
    public TelephoneField(String value) { super("tel", value); }
}
