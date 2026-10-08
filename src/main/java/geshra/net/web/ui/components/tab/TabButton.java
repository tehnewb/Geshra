package geshra.net.web.ui.components.tab;

import geshra.net.web.ui.components.Button;

/**
 * Renders one tab selector and routes clicks to its containing Tabbed component using its current index.
 */
public class TabButton extends Button {
    protected Tabbed tabbed; // Containing tab component used by click handlers.
    int index; // Current tab position used for selection without narrowing large indices.

    /**
     * Creates an unattached tab button component; attachment and DOM updates are owned by its session UI.
     *
     * @param label tab button label
     * @param index existing or selected tab index
     */
    public TabButton(String label, int index) {
        super(label);
        this.index = index;
    }

    @Override
    protected void create() {
        super.create();

        this.addClassName("tab-button");
        this.addClickListener(ignored -> tabbed.selectTab(index));
    }
}
