package geshra.net.web.ui.components;

/**
 * Native week input with browser-owned editing, validation, and value sanitization.
 * Programmatic values follow JavaScript and do not fire input or change callbacks.
 */
public class WeekField extends NativeTextField {
    /**
     * Creates an empty native week field.
     */
    public WeekField() { super("week"); }

    /**
     * Creates a native week field with its initial value.
     * @param value initial browser value
     */
    public WeekField(String value) { super("week", value); }
}
