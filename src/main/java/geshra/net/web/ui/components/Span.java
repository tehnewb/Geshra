package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;

/**
 * Renders an inline span with mutable text, queuing browser text updates through Component.
 */
public class Span extends Component {

    private String text; // Text rendered by this component.

    /**
     * Creates an unattached span component; attachment and DOM updates are owned by its session UI.
     */
    public Span() {
        super("span");
    }

    /**
     * Creates an unattached span component; attachment and DOM updates are owned by its session UI.
     *
     * @param text text to render; null leaves initial content empty
     */
    public Span(String text) {
        this();
        this.text = text;
    }

    /**
     * Updates the text and queues the corresponding browser change.
     *
     * @param text text to render; null leaves initial content empty
     * @return this component for chaining
     */
    public Span setText(String text) {
        this.text = text;
        this.queueForDispatch(DOMUpdateType.SET_TEXT, DOMUpdateParam.TEXT, text);
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
