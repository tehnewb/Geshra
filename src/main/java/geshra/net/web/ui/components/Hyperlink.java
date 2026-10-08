package geshra.net.web.ui.components;

import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;
import java.util.Map;

/**
 * Renders an anchor with mutable link text and destination. Text and href updates are dispatched through the owning session UI.
 */
public class Hyperlink extends TextComponent {

    private String link; // Anchor destination URL.

    /**
     * Creates an unattached hyperlink component; attachment and DOM updates are owned by its session UI.
     *
     * @param text text to render; null leaves initial content empty
     * @param link link supplied to this operation
     */
    public Hyperlink(String text, String link) {
        super(text, "a");
        this.link = link;
    }

    /**
     * Updates the link and queues the corresponding browser change.
     *
     * @param link link supplied to this operation
     */
    public void setLink(String link) {
        this.link = link;

        this.queueForDispatch(DOMUpdateType.SET_ATTRIBUTE, DOMUpdateParam.KEY, "href", DOMUpdateParam.VALUE, link);
        this.push();
    }

    @Override
    protected void create() {
        super.create();
        this.queueForDispatch(DOMUpdateType.SET_ATTRIBUTE, DOMUpdateParam.KEY, "href", DOMUpdateParam.VALUE, link);
    }
}
