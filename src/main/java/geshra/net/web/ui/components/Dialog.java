package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native dialog using show, showModal, and close. The browser owns focus restoration, modal
 * inertness, Escape cancellation, and the top layer. Method errors remain asynchronous browser
 * errors. Listen to close and cancel using the standard native event API.
 */
public class Dialog extends Component {
    private String returnValue = ""; // Last return value received from a native close event.

    /**
     * Creates a closed dialog.
     * @param content dialog content
     */
    public Dialog(Component... content) { super("dialog"); attribute("class", "geshra-dialog"); add(content); listen("close", event -> returnValue = event.getValue()); }

    /**
     * Requests native nonmodal display.
     * @return this dialog
     */
    public Dialog show() { callMethod("show"); return this; }

    /**
     * Requests native modal display.
     * @return this dialog
     */
    public Dialog showModal() { callMethod("showModal"); return this; }

    /**
     * Requests native close with a return value.
     * @param value native return value
     * @return this dialog
     */
    public Dialog close(String value) { callMethod("close", value); return this; }

    /**
     * Requests native close without changing the native return value.
     * @return this dialog
     */
    public Dialog close() { callMethod("close"); return this; }

    /**
     * Returns the latest browser-reported close result.
     * @return return value
     */
    public String getReturnValue() { return returnValue; }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }

}
