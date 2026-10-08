package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Lightweight GridLayout container using reusable stylesheet classes. Native child order, focus,
 * and browser layout remain intact; construction does not allocate a CSS model for each child.
 */
public class GridLayout extends Div {

    /**
     * Sets an explicit equal-width column count.
     * @param columns positive column count
     * @return this grid
     */
    public GridLayout setColumns(int columns) {
        if (columns <= 0) throw new IllegalArgumentException("Columns must be positive");
        getStyle()
                .set("grid-template-columns", "repeat(" + columns + ",minmax(0,1fr))");
        return this;
    }
    /**
     * Creates an empty GridLayout container.
     */
    public GridLayout() { attribute("class", "geshra-grid"); }

    /**
     * Creates a GridLayout container with initial children.
     * @param children initial child components
     */
    public GridLayout(Component... children) { this(); add(children); }
}
