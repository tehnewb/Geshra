package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native option group with an accessible label and nested Option children.
 */
public class OptionGroup extends Component {
    /**
     * Creates a labeled option group.
     * @param label native group label
     * @param options native options
     */
    public OptionGroup(String label, Option... options) { super("optgroup"); attribute("label", label); add(options); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }

}
