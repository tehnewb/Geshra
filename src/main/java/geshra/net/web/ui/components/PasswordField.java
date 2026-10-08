package geshra.net.web.ui.components;

/**
 * Native password input with browser-owned editing, validation, and value sanitization.
 * Programmatic values follow JavaScript and do not fire input or change callbacks.
 */
public class PasswordField extends NativeTextField {
    /**
     * Creates an empty native password field.
     */
    public PasswordField() { super("password"); }

    /**
     * Creates a native password field with its initial value.
     * @param value initial browser value
     */
    public PasswordField(String value) { super("password", value); }
}
