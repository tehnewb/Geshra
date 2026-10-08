package geshra.net.web.ui.event;

import geshra.event.Event;
import geshra.event.EventTypes;
import geshra.net.web.ui.Component;

/**
 * Identifies a component whose detach notification is being dispatched synchronously. The component reference is borrowed; listeners do not own its cleanup.
 */
public class DetachEvent extends Event {

    private final Component component; // Component targeted by this browser event.

    /**
     * Creates an event retaining the supplied payload for synchronous dispatch.
     *
     * @param component component to associate or attach
     */
    public DetachEvent(Component component) {
        super(EventTypes.DETACH);
        this.component = component;
    }

    /**
     * Returns the component retained by this instance.
     * @return the resulting component value
     */
    public Component getComponent() {
        return component;
    }
}
