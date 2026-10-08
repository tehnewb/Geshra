package geshra.net.web.ui;

import geshra.event.EventHandler;
import geshra.net.web.ui.event.DomEvent;

/**
 * Filters a typed browser notification by its exact native event name.
 * @param name native event name
 * @param delegate application handler
 * @param preventDefault whether the browser cancels the native default action synchronously
 */
record DomEventBinding(String name, EventHandler<DomEvent> delegate, boolean preventDefault) implements EventHandler<DomEvent> {
    @Override
    public void handle(DomEvent event) {
        if (name.equals(event.getName())) delegate.handle(event);
    }
}
