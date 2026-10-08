package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.BrowserJson;
import geshra.net.web.ui.DOMUpdateType;
import geshra.net.web.ui.DOMUpdateParam;

/**
 * Native canvas with a batched 2D drawing API. Commands call the actual CanvasRenderingContext2D;
 * no animation loop or scene graph is retained. Method return values stay in the browser.
 */
public class Canvas extends Component {
    /**
     * Creates an empty canvas container.
     */
    public Canvas() { super("canvas"); }

    /**
     * Creates a canvas container with child components.
     * @param children initial children
     */
    public Canvas(Component... children) { this(); add(children); }

    /**
     * Creates a canvas with explicit bitmap dimensions.
     * @param width non-negative bitmap width
     * @param height non-negative bitmap height
     */
    public Canvas(int width, int height) {
        this();
        if (width < 0 || height < 0) throw new IllegalArgumentException("Canvas dimensions cannot be negative");
        setProperty("width", width);
        setProperty("height", height);
    }

    /**
     * Calls a native 2D drawing method with typed arguments.
     * @param method context method such as fillRect, strokeText, or drawImage
     * @param arguments native arguments; components resolve to their browser elements
     * @return this canvas
     */
    public Canvas draw(String method, Object... arguments) {
        queueForDispatch(DOMUpdateType.CANVAS_METHOD, DOMUpdateParam.KEY, method, DOMUpdateParam.VALUE, BrowserJson.encode(arguments));
        push();
        return this;
    }

    /**
     * Sets a native 2D drawing property such as fillStyle, lineWidth, or font.
     * @param property native context property
     * @param value JSON-compatible property value
     * @return this canvas
     */
    public Canvas setDrawingProperty(String property, Object value) {
        queueForDispatch(DOMUpdateType.CANVAS_PROPERTY, DOMUpdateParam.KEY, property, DOMUpdateParam.VALUE, BrowserJson.encode(value));
        push();
        return this;
    }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
