package geshra.net.web.ui.components;

/**
 * Native email input with browser-owned editing, validation, and value sanitization.
 * Programmatic values follow JavaScript and do not fire input or change callbacks.
 */
public class EmailField extends NativeTextField {
    /**
     * Creates an empty native email field.
     */
    public EmailField() { super("email"); }

    /**
     * Creates a native email field with its initial value.
     * @param value initial browser value
     */
    public EmailField(String value) { super("email", value); }
}
