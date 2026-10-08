package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;

/**
 * Renders an HTML element with mutable text content. Changes queue DOM text updates and flush when attached; null initial text leaves the element empty.
 */
public class TextComponent extends Component {

    private String text; // Text rendered by this component.

    /**
     * Creates an unattached text component component; attachment and DOM updates are owned by its session UI.
     *
     * @param text text to render; null leaves initial content empty
     * @param tag HTML element tag
     */
    public TextComponent(String text, String tag) {
        super(tag);
        this.text = text;
    }

    /**
     * Updates the text and queues the corresponding browser change.
     *
     * @param text text to render; null leaves initial content empty
     */
    public void setText(String text) {
        discardChildren();
        this.text = text;
        this.queueForDispatch(DOMUpdateType.SET_TEXT, DOMUpdateParam.TEXT, text == null ? "" : text);
        push();
    }

    /**
     * Returns the text retained by this instance.
     * @return the resulting text value
     */
    public String getText() {
        return text;
    }

    @Override
    protected void create() {
        if (text == null)
            return;
        this.queueForDispatch(DOMUpdateType.SET_TEXT, DOMUpdateParam.TEXT, text);
    }

    @Override
    protected void destroy() {

    }
}
