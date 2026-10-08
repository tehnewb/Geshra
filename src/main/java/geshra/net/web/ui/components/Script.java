package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;
import java.util.Map;

/**
 * Renders a script element with an optional external source URL. A null source creates an empty element; this component does not execute Java code.
 */
public class Script extends Component {

    private final String source; // External script URL; null creates an empty script element.

    /**
     * Creates an unattached script component; attachment and DOM updates are owned by its session UI.
     *
     * @param source source supplied to this operation
     */
    public Script(String source) {
        super("script");

        this.source = source;
    }

    /**
     * Creates an unattached script component; attachment and DOM updates are owned by its session UI.
     */
    public Script() {
        this(null);
    }

    @Override
    protected void create() {
        if (source == null)
            return;
        this.queueForDispatch(DOMUpdateType.SET_ATTRIBUTE, DOMUpdateParam.KEY, "src", DOMUpdateParam.VALUE, source);
    }

    @Override
    protected void destroy() {

    }
}
