package geshra.net.web.ui;

import geshra.net.web.ui.components.Div;

/**
 * Exercises cleanup when application content fails during its real attachment lifecycle.
 */
final class FailingVirtualContent extends Div {
    /**
     * Creates content whose attachment is intentionally rejected by application code.
     */
    FailingVirtualContent() { }

    @Override
    protected void create() { throw new IllegalStateException("content attachment failed"); }
}
