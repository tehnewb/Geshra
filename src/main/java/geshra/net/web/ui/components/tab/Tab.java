package geshra.net.web.ui.components.tab;

import geshra.net.web.ui.Component;

/**
 * Associates a tab button with its content wrapper. Both references belong to the containing Tabbed component; this immutable association does not own component cleanup.
 */
record Tab(TabButton button, Component content) {
}
