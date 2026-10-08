package geshra.net.web.ui.components;

import geshra.net.web.ui.css.*;
import geshra.net.web.ui.Component;

/**
 * Groups child components in a vertical flex layout on the owning session UI.
 */
public class VerticalLayout extends Div {

    /**
     * Creates a vertical layout containing the supplied children.
     * @param children initial children
     */
    public VerticalLayout(Component... children) { add(children); }

    @Override
    protected void create() {
        this.getStyle()
                .display(Display.FLEX)
                .flexDirection(FlexDirection.COLUMN)
                .gap("1em");
    }
}
