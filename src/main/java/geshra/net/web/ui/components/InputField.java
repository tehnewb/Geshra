package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Provides the common HTML input element lifecycle for concrete input controls.
 */
public class InputField extends Component {

    /**
     * Creates an unattached input field component; attachment and DOM updates are owned by its session UI.
     */
    public InputField() {
        super("input");
    }

    @Override
    protected void create() {

    }

    @Override
    protected void destroy() {

    }
}
