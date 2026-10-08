package geshra.net.web.ui.components;

import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;

/**
 * Native number input. An empty value maps to null; getValueAsNumber returns NaN for that state,
 * matching the DOM API. The browser owns step, min/max validation, spin buttons, and sanitization.
 */
public class NumberField extends ValueComponent<Double> {
    /**
     * Creates an empty number input.
     */
    public NumberField() { this(null); }

    /**
     * Creates a number input with an initial value.
     * @param value initial number or null
     */
    public NumberField(Double value) { super("input", value); }

    /**
     * Returns the numeric value or NaN for an empty input.
     * @return native-style numeric value
     */
    public double getValueAsNumber() { return getValue() == null ? Double.NaN : getValue(); }

    /**
     * Sets the native minimum constraint.
     * @param min minimum value
     * @return this field
     */
    public NumberField setMin(double min) { setProperty("min", Double.toString(min)); return this; }

    /**
     * Sets the native maximum constraint.
     * @param max maximum value
     * @return this field
     */
    public NumberField setMax(double max) { setProperty("max", Double.toString(max)); return this; }

    /**
     * Sets the native step constraint; use anyStep for unrestricted decimals.
     * @param step positive step
     * @return this field
     */
    public NumberField setStep(double step) {
        if (!(step > 0) || !Double.isFinite(step)) throw new IllegalArgumentException("Step must be positive and finite");
        setProperty("step", Double.toString(step));
        return this;
    }

    /**
     * Allows arbitrary native decimal steps.
     * @return this field
     */
    public NumberField anyStep() { setProperty("step", "any"); return this; }

    /**
     * Returns the native input type, overridable by range controls.
     * @return input type
     */
    protected String inputType() { return "number"; }

    @Override
    public Double deconstruct(String value) { return value.isEmpty() ? null : Double.valueOf(value); }

    @Override
    public String construct(Double value) { return value == null || !Double.isFinite(value) ? "" : Double.toString(value); }

    @Override
    protected void create() {
        queueForDispatch(DOMUpdateType.SET_TYPE, DOMUpdateParam.TYPE, inputType());
        super.create();
    }
}
