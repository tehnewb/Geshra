package geshra.net.web.ui.components;

/**
 * Native url input with browser-owned editing, validation, and value sanitization.
 * Programmatic values follow JavaScript and do not fire input or change callbacks.
 */
public class UrlField extends NativeTextField {
    /**
     * Creates an empty native url field.
     */
    public UrlField() { super("url"); }

    /**
     * Creates a native url field with its initial value.
     * @param value initial browser value
     */
    public UrlField(String value) { super("url", value); }
}
