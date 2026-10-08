package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Renders an HTML image using a source URL and descriptive text supplied at construction.
 */
public class Image extends Component {

    private final String src; // Image resource URL passed to the browser.
    private final String description; // Descriptive text associated with the image.

    /**
     * Creates an unattached image component; attachment and DOM updates are owned by its session UI.
     *
     * @param src src supplied to this operation
     * @param description description supplied to this operation
     */
    public Image(String src, String description) {
        super("img");
        this.src = src;
        this.description = description;
    }

    @Override
    protected void create() {
        this.setAttribute("src", src);
        this.setAttribute("description", description);
    }

    @Override
    protected void destroy() {

    }
}
