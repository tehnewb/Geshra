package geshra.net.web.ui.components;

import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;

/**
 * Shared native input implementation for string-valued HTML input types. The type is applied before
 * the initial value so the browser performs its own sanitization. Native validity, focus, selection,
 * and keyboard behavior remain browser-owned; server values synchronize without synthetic events.
 */
public class NativeTextField extends ValueComponent<String> {
    private final String type; // Native HTML input type.

    /**
     * Creates an empty field of the specified native type.
     * @param type HTML input type
     */
    public NativeTextField(String type) { this(type, ""); }

    /**
     * Creates a field with an initial string value.
     * @param type HTML input type
     * @param value initial value
     */
    public NativeTextField(String type, String value) { super("input", value); this.type = type; }

    /**
     * Sets the browser placeholder.
     * @param placeholder placeholder text
     * @return this field
     */
    public NativeTextField setPlaceholder(String placeholder) { setProperty("placeholder", placeholder); return this; }

    /**
     * Sets required without changing the value or firing events.
     * @param required whether native form validation requires a value
     * @return this field
     */
    public NativeTextField setRequired(boolean required) { setProperty("required", required); return this; }

    /**
     * Sets readOnly using its native Boolean property.
     * @param readOnly whether user editing is disabled
     * @return this field
     */
    public NativeTextField setReadOnly(boolean readOnly) { setProperty("readOnly", readOnly); return this; }

    /**
     * Sets the maximum UTF-16 input length, matching maxLength.
     * @param length non-negative limit
     * @return this field
     */
    public NativeTextField setMaxLength(int length) {
        if (length < 0) throw new IllegalArgumentException("maxLength cannot be negative");
        setProperty("maxLength", length);
        return this;
    }

    @Override
    public String deconstruct(String value) { return value; }

    @Override
    public String construct(String value) { return value == null ? "" : value; }

    @Override
    protected void create() {
        queueForDispatch(DOMUpdateType.SET_TYPE, DOMUpdateParam.TYPE, type);
        super.create();
    }
}
