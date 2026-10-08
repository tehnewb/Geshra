package geshra.net.web.ui.components;



/**
 * Native multiline text input. Newlines, selection, validation, and input events follow the browser.
 */
public class TextArea extends ValueComponent<String> {
    /**
     * Creates an empty multiline input.
     */
    public TextArea() { this(""); }

    /**
     * Creates a multiline input.
     * @param value initial text
     */
    public TextArea(String value) { super("textarea", value); }

    /**
     * Sets the native row count.
     * @param rows positive row count
     * @return this input
     */
    public TextArea setRows(int rows) { if (rows <= 0) throw new IllegalArgumentException("Rows must be positive"); setProperty("rows", rows); return this; }

    /**
     * Sets the native placeholder.
     * @param text hint text
     * @return this input
     */
    public TextArea setPlaceholder(String text) { setProperty("placeholder", text); return this; }

    /**
     * Sets the native read-only state.
     * @param readOnly editing disabled
     * @return this input
     */
    public TextArea setReadOnly(boolean readOnly) { setProperty("readOnly", readOnly); return this; }

    @Override
    public String deconstruct(String value) { return value; }

    @Override
    public String construct(String value) { return value == null ? "" : value; }

}
