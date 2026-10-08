package geshra.net.web.ui.components;

/**
 * Native hidden input with browser-owned editing, validation, and value sanitization.
 * Programmatic values follow JavaScript and do not fire input or change callbacks.
 */
public class HiddenField extends NativeTextField {
    /**
     * Creates an empty native hidden field.
     */
    public HiddenField() { super("hidden"); }

    /**
     * Creates a native hidden field with its initial value.
     * @param value initial browser value
     */
    public HiddenField(String value) { super("hidden", value); }
}
