package geshra.net.web.ui.css;

import geshra.event.Event;
import geshra.event.EventTypes;

/**
 * Carries one CSS property and its new text value to the owning component. Dispatch is synchronous and
 * the property/value references are borrowed immutable strings.
 */
public class StyleChangeEvent extends Event {

    private final String property; // CSS property changed by this event.
    private final String value; // CSS keyword emitted for this enum constant.

    /**
     * Associates this constant with the CSS keyword emitted when serializing declarations.
     *
     * @param property property supplied to this operation
     * @param value application value or CSS text to retain
     */
    public StyleChangeEvent(String property, String value) {
        super(EventTypes.STYLE_CHANGE);
        this.property = property;
        this.value = value;
    }

    /**
     * Returns the property retained by this instance.
     * @return the resulting property value
     */
    public String getProperty() {
        return property;
    }

    /**
     * Returns the value retained by this instance.
     * @return the resulting value value
     */
    public String getValue() {
        return value;
    }
}
