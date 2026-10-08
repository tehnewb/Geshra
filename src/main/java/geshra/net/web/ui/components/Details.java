package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native disclosure with a Summary and retained content. Browser toggling preserves focus and
 * keyboard behavior. Grouping by name uses native exclusive disclosure behavior where supported.
 */
public class Details extends Component {
    private boolean open; // Latest requested or browser-reported open state.

    /**
     * Creates a closed disclosure.
     * @param title summary text
     * @param content disclosed children
     */
    public Details(String title, Component... content) {
        super("details");
        add(new Summary(title));
        add(content);
        listen("toggle", event -> open = event.getValue()
                .equals("open"));
    }

    /**
     * Sets the native open property; the browser sends its resulting toggle event.
     * @param open requested state
     * @return this disclosure
     */
    public Details setOpen(boolean open) { this.open = open; setProperty("open", open); return this; }

    /**
     * Returns the latest requested or browser-reported state.
     * @return open state
     */
    public boolean isOpen() { return open; }

    /**
     * Sets a native exclusive group name.
     * @param name group name
     * @return this disclosure
     */
    public Details setGroup(String name) { attribute("name", name); return this; }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }

}
