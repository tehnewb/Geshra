package geshra.net.web.ui.components;



/**
 * Native single selection dropdown. Values remain strings, including empty values and punctuation.
 * An untouched select lets the browser select its first option. Explicit setValue follows the native
 * value assignment rules and never fires input or change callbacks.
 */
public class Select extends ValueComponent<String> {
    private boolean assigned; // Whether selection was explicitly requested before attachment.

    /**
     * Creates a select with native default selection.
     */
    public Select() { super("select", ""); }

    /**
     * Appends a native option.
     * @param value submitted string
     * @param label visible label
     * @return this select
     */
    public Select addOption(String value, String label) { add(new Option(value, label)); return this; }

    @Override
    public String deconstruct(String value) { return value; }

    @Override
    public String construct(String value) { return value == null ? "" : value; }

    @Override
    public void setValue(String value) { assigned = true; super.setValue(value); }

    @Override
    protected void create() { if (assigned) super.create(); }

}
