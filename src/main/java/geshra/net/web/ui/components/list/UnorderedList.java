package geshra.net.web.ui.components.list;

import geshra.net.web.ui.Component;

/**
 * Renders an unordered HTML list and wraps appended content in list item components.
 */
public class UnorderedList extends Component {

    /**
     * Creates an unattached unordered list component; attachment and DOM updates are owned by its session UI.
     */
    public UnorderedList() {
        super("ul");
    }

    /**
     * Wraps the supplied component in a list item and attaches it to this list.
     *
     * @param component component to associate or attach
     * @return the resulting add list item value
     */
    public ListItem addListItem(Component component) {
        ListItem item = new ListItem();
        item.add(component);
        return item;
    }

    @Override
    protected void create() {

    }

    @Override
    protected void destroy() {

    }

}
