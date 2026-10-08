package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;
import java.util.Map;

/**
 * Renders a document link element with an href and relationship, such as a stylesheet resource.
 */
public class Link extends Component {

    private String href; // Resource URL for the link element.
    private String rel; // Relationship between this resource and the current document.

    /**
     * Creates an unattached link component; attachment and DOM updates are owned by its session UI.
     *
     * @param href href supplied to this operation
     * @param rel rel supplied to this operation
     */
    public Link(String href, String rel) {
        super("link");
        this.href = href;
        this.rel = rel;
    }

    /**
     * Updates the href and queues the corresponding browser change.
     *
     * @param href href supplied to this operation
     */
    public void setHref(String href) {
        this.queueForDispatch(DOMUpdateType.SET_ATTRIBUTE, DOMUpdateParam.KEY, "href", DOMUpdateParam.VALUE, this.href = href);
    }

    /**
     * Updates the rel and queues the corresponding browser change.
     *
     * @param rel rel supplied to this operation
     */
    public void setRel(String rel) {
        this.queueForDispatch(DOMUpdateType.SET_ATTRIBUTE, DOMUpdateParam.KEY, "rel", DOMUpdateParam.VALUE, this.rel = rel);
    }

    @Override
    protected void create() {
        this.queueForDispatch(DOMUpdateType.SET_ATTRIBUTE, DOMUpdateParam.KEY, "href", DOMUpdateParam.VALUE, href);
        this.queueForDispatch(DOMUpdateType.SET_ATTRIBUTE, DOMUpdateParam.KEY, "rel", DOMUpdateParam.VALUE, rel);
    }

    @Override
    protected void destroy() {

    }
}
