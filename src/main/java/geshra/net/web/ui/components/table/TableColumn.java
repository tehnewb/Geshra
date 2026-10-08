package geshra.net.web.ui.components.table;

import geshra.net.web.ui.components.TextComponent;

/**
 * Renders a table heading cell whose label identifies the column during row population.
 */
public class TableColumn extends TextComponent {

    /**
     * Creates an unattached table column component; attachment and DOM updates are owned by its session UI.
     *
     * @param text text to render; null leaves initial content empty
     */
    public TableColumn(String text) {
        super(text, "th");
    }

}
