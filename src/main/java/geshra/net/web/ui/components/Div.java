package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Renders a general-purpose HTML div container. Child attachment and DOM update ownership are handled by Component.
 */
public class Div extends Component {

    /**
     * Creates an unattached div component; attachment and DOM updates are owned by its session UI.
     *
     * @param children children supplied to this operation
     */
    public Div(Component... children) {
        super("div");
        this.add(children);
    }

    @Override
    protected void create() {
    }

    @Override
    protected void destroy() {

    }
}
