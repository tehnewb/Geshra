package geshra.net.web.ui.components;

import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;

/**
 * Native checkbox whose single Boolean value represents checked, including browser-originated
 * changes. Programmatic assignments are silent, as checked assignments are in JavaScript.
 * Indeterminate is a separate native visual property; it does not change checked or submission.
 */
public class Checkbox extends ValueComponent<Boolean> {
    /**
     * Creates an unchecked native checkbox.
     */
    public Checkbox() { this(false); }

    /**
     * Creates a checkbox with its initial checked state.
     * @param checked initial state
     */
    public Checkbox(boolean checked) { super("input", checked); }

    /**
     * Assigns checked without firing input or change events.
     * @param checked new state
     */
    public void setChecked(boolean checked) { setValue(checked); }

    /**
     * Returns the latest checked state, including native user input.
     * @return checked
     */
    public boolean isChecked() { return Boolean.TRUE.equals(getValue()); }

    /**
     * Assigns the native mixed-state visual indicator.
     * @param indeterminate mixed-state indicator
     * @return this checkbox
     */
    public Checkbox setIndeterminate(boolean indeterminate) { setProperty("indeterminate", indeterminate); return this; }

    @Override
    public Boolean deconstruct(String value) { return Boolean.parseBoolean(value); }

    @Override
    public String construct(Boolean value) { return Boolean.TRUE.equals(value) ? "true" : "false"; }

    @Override
    protected void create() {
        queueForDispatch(DOMUpdateType.SET_TYPE, DOMUpdateParam.TYPE, inputType());
        super.create();
    }

    /**
     * Returns the native checked-input type for checkbox and radio variants.
     * @return input type
     */
    protected String inputType() { return "checkbox"; }
}
