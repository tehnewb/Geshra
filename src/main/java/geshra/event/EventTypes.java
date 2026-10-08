package geshra.event;

import geshra.net.web.ui.css.StyleChangeEvent;
import geshra.net.web.ui.event.ClickEvent;
import geshra.net.web.ui.event.DetachEvent;
import geshra.net.web.ui.event.KeyDownEvent;
import geshra.net.web.ui.event.KeyUpEvent;
import geshra.net.web.ui.event.SubmitEvent;
import geshra.net.web.ui.event.ValueChangeEvent;
import geshra.net.web.ui.event.DomEvent;
import geshra.net.web.ui.event.ViewportEvent;

/**
 * Stable, dense numeric routes for built-in web UI events.
 * Event inheritance does not imply routing: register each required route explicitly.
 * Custom registries must use unique IDs and size their publishers to cover those IDs.
 */
public final class EventTypes {
    /**
     * Mouse-click route.
     */
    public static final EventType<ClickEvent> CLICK = new EventType<>(0, "click");
    /**
     * Component-detachment route.
     */
    public static final EventType<DetachEvent> DETACH = new EventType<>(1, "detach");
    /**
     * Key-down route.
     */
    public static final EventType<KeyDownEvent> KEY_DOWN = new EventType<>(2, "key-down");
    /**
     * Key-up route.
     */
    public static final EventType<KeyUpEvent> KEY_UP = new EventType<>(3, "key-up");
    /**
     * Form-submission route.
     */
    public static final EventType<SubmitEvent> SUBMIT = new EventType<>(4, "submit");
    /**
     * Input value-change route.
     */
    public static final EventType<ValueChangeEvent> VALUE_CHANGE = new EventType<>(5, "value-change");
    /**
     * CSS property-change route.
     */
    public static final EventType<StyleChangeEvent> STYLE_CHANGE = new EventType<>(6, "style-change");
    /**
     * Exclusive upper bound of the built-in route IDs.
     */
    public static final int COUNT = 9;
    /**
     * Native browser event route.
     */
    public static final EventType<DomEvent> DOM = new EventType<>(7, "dom");
    /**
     * Coalesced virtual viewport route.
     */
    public static final EventType<ViewportEvent> VIEWPORT = new EventType<>(8, "viewport");

    /**
     * Prevents instances of the built-in event type registry.
     */
    private EventTypes() {
        throw new AssertionError("No instances.");
    }
}
