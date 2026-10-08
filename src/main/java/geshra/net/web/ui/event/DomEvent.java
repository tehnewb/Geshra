package geshra.net.web.ui.event;

import geshra.event.Event;
import geshra.event.EventTypes;
import geshra.net.web.ui.Component;

/**
 * A native browser event delivered after it occurs, with its source, case-sensitive name, and a
 * value snapshot. Dialog close supplies returnValue; toggle supplies open/closed state; controls
 * supply their browser value. Server consumption cannot cancel an already-occurring browser event.
 */
public final class DomEvent extends Event {
    private final Component component; // Owning Java component.
    private final String name; // Case-sensitive native browser event name.
    private final String value; // Browser value or native state snapshot.

    /**
     * Creates a browser notification.
     * @param component source component
     * @param name event name
     * @param value browser snapshot
     */
    public DomEvent(Component component, String name, String value) {
        super(EventTypes.DOM);
        this.component = component;
        this.name = name;
        this.value = value;
    }

    /**
     * Returns the source component.
     * @return source
     */
    public Component getComponent() { return component; }

    /**
     * Returns the native event name.
     * @return name
     */
    public String getName() { return name; }

    /**
     * Returns the native value or state snapshot.
     * @return snapshot
     */
    public String getValue() { return value; }
}
