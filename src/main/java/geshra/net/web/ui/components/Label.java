package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;
import java.util.Map;

/**
 * Renders an HTML label with mutable text and an optional target control association. Updates are
 * queued through the owning session UI.
 */
public class Label extends Component {

    private String text; // Text rendered by this component.

    /**
     * Creates an unattached label component; attachment and DOM updates are owned by its session UI.
     *
     * @param text text to render; null leaves initial content empty
     */
    public Label(String text) {
        super("label");
        this.text = text;
    }

    /**
     * Creates visible label text associated with a native control.
     * @param text label text
     * @param control labeled control
     */
    public Label(String text, Component control) { this(text); forComponent(control); }

    /**
     * Returns the text retained by this instance.
     * @return the resulting text value
     */
    public String getText() {
        return text;
    }

    /**
     * Updates the text and queues the corresponding browser change.
     *
     * @param text text to render; null leaves initial content empty
     * @return this component for chaining
     */
    public Label setText(String text) {
        discardChildren();
        this.text = text;
        this.queueForDispatch(DOMUpdateType.SET_TEXT, DOMUpdateParam.TEXT, text == null ? "" : text);
        this.push();
        return this;
    }

    /**
     * Sets the label target to the component ID and flushes the queued attribute update.
     *
     * @param component component to associate or attach
     * @return this component for chaining
     */
    public Label forComponent(Component component) {
        this.queueForDispatch(DOMUpdateType.SET_ATTRIBUTE, DOMUpdateParam.KEY, "for", DOMUpdateParam.VALUE, String.valueOf(component.getComponentID()));
        this.push();
        return this;
    }

    @Override
    protected void create() {
        this.queueForDispatch(DOMUpdateType.SET_TEXT, DOMUpdateParam.TEXT, text);
    }

    @Override
    protected void destroy() {

    }
}
