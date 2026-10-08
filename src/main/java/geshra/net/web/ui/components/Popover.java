package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native automatic popover with browser light-dismiss, Escape, and top-layer placement.
 * Listen to toggle for visibility transitions. Programmatic calls retain native activation rules.
 */
public class Popover extends Div {
    /**
     * Creates a native automatic popover.
     * @param content popover children
     */
    public Popover(Component... content) { attribute("popover", "auto"); add(content); }

    /**
     * Shows the native popover.
     * @return this popover
     */
    public Popover show() { callMethod("showPopover"); return this; }

    /**
     * Hides the native popover.
     * @return this popover
     */
    public Popover hide() { callMethod("hidePopover"); return this; }

    /**
     * Toggles native popover visibility.
     * @return this popover
     */
    public Popover toggle() { callMethod("togglePopover"); return this; }


}
