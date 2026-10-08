package geshra.net.web.ui.components.table;

import geshra.net.web.ui.Component;

/**
 * Renders the table body container that owns application row components.
 */
public class TableBody extends Component {

    /**
     * Creates an unattached table body component; attachment and DOM updates are owned by its session UI.
     */
    public TableBody() {
        super("tbody");
    }

    @Override
    protected void create() {

    }

    @Override
    protected void destroy() {

    }
}
