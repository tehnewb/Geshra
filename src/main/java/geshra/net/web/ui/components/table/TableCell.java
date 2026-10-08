package geshra.net.web.ui.components.table;

import geshra.net.web.ui.components.TextComponent;

/**
 * Renders a table data cell with initial text and optional child components.
 */
public class TableCell extends TextComponent {

    /**
     * Creates an unattached table cell component; attachment and DOM updates are owned by its session UI.
     *
     * @param text text to render; null leaves initial content empty
     */
    public TableCell(String text) {
        super(text, "td");
    }

    /**
     * Creates an unattached table cell component; attachment and DOM updates are owned by its session UI.
     */
    public TableCell() {
        this(null);
    }
}
