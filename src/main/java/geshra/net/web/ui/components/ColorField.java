package geshra.net.web.ui.components;

/**
 * Native color input with browser-owned editing, validation, and value sanitization.
 * Programmatic values follow JavaScript and do not fire input or change callbacks.
 */
public class ColorField extends NativeTextField {
    /**
     * Creates an empty native color field.
     */
    public ColorField() { super("color"); }

    /**
     * Creates a native color field with its initial value.
     * @param value initial browser value
     */
    public ColorField(String value) { super("color", value); }
}
