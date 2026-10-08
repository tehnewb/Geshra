package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native progress indicator. Removing value selects the indeterminate state.
 */
public class Progress extends Component {
    /**
     * Creates a native indicator.
     * @param value initial value
     * @param max native maximum
     */
    public Progress(double value, double max) { super("progress"); setMax(max); setValue(value); }

    /**
     * Sets the native numeric value.
     * @param value numeric value
     * @return this indicator
     */
    public Progress setValue(double value) { setProperty("value", value); return this; }

    /**
     * Sets the native maximum.
     * @param max numeric maximum
     * @return this indicator
     */
    public Progress setMax(double max) { setProperty("max", max); return this; }

    /**
     * Removes the value attribute, selecting native indeterminate display.
     * @return this indicator
     */
    public Progress setIndeterminate() { removeAttribute("value"); return this; }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }

}
