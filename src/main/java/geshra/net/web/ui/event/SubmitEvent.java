package geshra.net.web.ui.event;

import geshra.event.Event;
import geshra.event.EventTypes;
import java.util.Map;

/**
 * Carries form field values from one browser submission. The supplied map is retained without copying; callers must not mutate it during dispatch. Typed accessors rely on the application knowing each stored value type.
 */
@SuppressWarnings("unchecked")
public class SubmitEvent extends Event {

    private final Map<String, Object> values; // Submitted values indexed by form field name.

    /**
     * Creates an event retaining the supplied payload for synchronous dispatch.
     *
     * @param values values supplied to this operation
     */
    public SubmitEvent(Map<String, Object> values) {
        super(EventTypes.SUBMIT);
        this.values = values;
    }

    /**
     * Retrieves a submitted field with an unchecked application-selected type; absent or null fields use the supplied fallback when present.
     *
     * @param name form field name
     * @return the resulting value value
     * @param <T> application-selected value type
     */
    public <T> T getValue(String name) {
        return (T) values.get(name);
    }

    /**
     * Retrieves a submitted field with an unchecked application-selected type; absent or null fields use the supplied fallback when present.
     *
     * @param name form field name
     * @param defaultValue fallback when the field is absent or null
     * @return the resulting value value
     * @param <T> application-selected value type
     */
    public <T> T getValue(String name, T defaultValue) {
        Object value = values.get(name);
        if (value == null)
            return defaultValue;
        return (T) value;
    }

    /**
     * Returns the values retained by this instance.
     * @return the resulting values value
     */
    public Map<String, Object> getValues() {
        return values;
    }
}
