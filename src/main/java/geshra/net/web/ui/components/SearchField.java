package geshra.net.web.ui.components;

/**
 * Native search input with browser-owned editing, validation, and value sanitization.
 * Programmatic values follow JavaScript and do not fire input or change callbacks.
 */
public class SearchField extends NativeTextField {
    /**
     * Creates an empty native search field.
     */
    public SearchField() { super("search"); }

    /**
     * Creates a native search field with its initial value.
     * @param value initial browser value
     */
    public SearchField(String value) { super("search", value); }
}
