package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Lightweight ButtonBar container using reusable stylesheet classes. Native child order, focus,
 * and browser layout remain intact; construction does not allocate a CSS model for each child.
 */
public class ButtonBar extends HorizontalLayout {
    /**
     * Creates an empty ButtonBar container.
     */
    public ButtonBar() { attribute("class", "geshra-button-bar"); }

    /**
     * Creates a ButtonBar container with initial children.
     * @param children initial child components
     */
    public ButtonBar(Component... children) { this(); add(children); }
}
