package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native scalar measurement with browser min/max, low/high, and optimum semantics.
 */
public class Meter extends Component {
    /**
     * Creates a native indicator.
     * @param value initial value
     * @param max native maximum
     */
    public Meter(double value, double max) { super("meter"); setMax(max); setValue(value); }

    /**
     * Sets the native numeric value.
     * @param value numeric value
     * @return this indicator
     */
    public Meter setValue(double value) { setProperty("value", value); return this; }

    /**
     * Sets the native maximum.
     * @param max numeric maximum
     * @return this indicator
     */
    public Meter setMax(double max) { setProperty("max", max); return this; }

    /**
     * Sets the native minimum.
     * @param min numeric minimum
     * @return this indicator
     */
    public Meter setMin(double min) { setProperty("min", min); return this; }

    /**
     * Sets the native optimum.
     * @param optimum preferred value
     * @return this indicator
     */
    public Meter setOptimum(double optimum) { setProperty("optimum", optimum); return this; }

    /**
     * Sets the native low and high thresholds.
     * @param low lower threshold
     * @param high upper threshold
     * @return this indicator
     */
    public Meter setThresholds(double low, double high) { setProperty("low", low); setProperty("high", high); return this; }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }

}
