package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Lightweight Card container using reusable stylesheet classes. Native child order, focus,
 * and browser layout remain intact; construction does not allocate a CSS model for each child.
 */
public class Card extends Div {
    /**
     * Creates an empty Card container.
     */
    public Card() { attribute("class", "geshra-card"); }

    /**
     * Creates a Card container with initial children.
     * @param children initial child components
     */
    public Card(Component... children) { this(); add(children); }
}
