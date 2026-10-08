package geshra.net.web.ui.components.table;

import geshra.net.web.ui.Component;

/**
 * Renders the table header container that owns column heading components.
 */
public class TableHead extends Component {

    /**
     * Creates an unattached table head component; attachment and DOM updates are owned by its session UI.
     */
    public TableHead() {
        super("thead");
    }

    @Override
    protected void create() {

    }

    @Override
    protected void destroy() {

    }
}
