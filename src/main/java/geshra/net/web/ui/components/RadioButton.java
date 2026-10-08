package geshra.net.web.ui.components;



/**
 * Native radio button. Matching name and form associate mutually exclusive buttons. Checked is
 * a Boolean; submissionValue is the independent string sent when the native form is submitted.
 */
public class RadioButton extends Checkbox {
    /**
     * Creates an unchecked native radio button.
     * @param name native radio group name
     * @param submissionValue native form value
     */
    public RadioButton(String name, String submissionValue) { super(false); attribute("name", name); setProperty("value", submissionValue); }

    @Override
    protected String inputType() { return "radio"; }

}
