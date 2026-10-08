package geshra.net.web.ui.components.list;

import geshra.net.web.ui.Component;

/**
 * Renders an HTML list item whose child components provide its content.
 */
public class ListItem extends Component {

    /**
     * Creates an unattached list item component; attachment and DOM updates are owned by its session UI.
     */
    public ListItem() {
        super("li");
    }

    @Override
    protected void create() {

    }

    @Override
    protected void destroy() {

    }
}
