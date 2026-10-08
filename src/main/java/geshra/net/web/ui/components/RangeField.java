package geshra.net.web.ui.components;



/**
 * Native range slider with browser clamping, stepping, keyboard controls, and pointer input.
 */
public class RangeField extends NumberField {
    /**
     * Creates a native slider at its default midpoint of 50.
     */
    public RangeField() { this(50.0); }

    /**
     * Creates a slider with an initial value.
     * @param value initial number
     */
    public RangeField(double value) { super(value); }

    @Override
    protected String inputType() { return "range"; }

}
