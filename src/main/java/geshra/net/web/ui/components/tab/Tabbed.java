package geshra.net.web.ui.components.tab;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.components.Div;
import geshra.net.web.ui.event.KeyDownEvent;
import geshra.net.web.ui.css.*;

import java.util.ArrayList;

/**
 * Coordinates a horizontal ARIA tablist and linked tab panels. Each tab owns a wrapper whose visibility
 * follows the selected index; no tab is selected initially. One enabled button is a keyboard tab stop.
 * Left/Right/Home/End move focus and activate the corresponding enabled tab; native Enter/Space and
 * pointer activation retain button behavior. All panels are already present, so activation never
 * fetches remote content. Add, insert and select on the owning session event loop.
 */
public class Tabbed extends Div {

    private final Div buttonContainer; // Container for the tab selector buttons.
    private final Div contentContainer; // Container for all tab content wrappers.

    private final ArrayList<Tab> tabs = new ArrayList<>(); // Tab button/content associations in display order.
    private int selectedTab = -1; // Selected tab index; minus one means none.

    /**
     * Creates an unattached tabbed component; attachment and DOM updates are owned by its session UI.
     */
    public Tabbed() {
        super();
        this.buttonContainer = new Div();
        this.buttonContainer.attribute("role", "tablist");
        this.buttonContainer.ariaLabel("Tabs");
        this.contentContainer = new Div();
    }

    /**
     * Appends a button and initially hidden content wrapper; selection remains unchanged.
     *
     * @param label tab button label
     * @param component component to associate or attach
     * @return this component for chaining
     */
    public Tabbed addTab(String label, Component component) {
        TabButton button = new TabButton(label, tabs.size());

        Div wrapper = new Div();
        wrapper.getStyle()
                .display(Display.NONE)
                .overflow(Overflow.AUTO);
        wrapper.add(component);
        configureTab(button, wrapper);

        buttonContainer.add(button);
        contentContainer.add(wrapper);

        Tab tab = new Tab(button, wrapper);
        tab.button()
                .tabbed = this;
        tabs.add(tab);
        updateTabStops();
        return this;
    }

    /**
     * Inserts a tab button before the requested existing index and updates subsequent button indices.
     *
     * @param index existing or selected tab index
     * @param label tab button label
     * @param component component to associate or attach
     * @return this component for chaining
     */
    public Tabbed insertTab(int index, String label, Component component) {
        if (index < 0 || index > tabs.size())
            throw new IndexOutOfBoundsException(index);
        if (index == tabs.size()) return addTab(label, component);
        TabButton button = new TabButton(label, index);

        Div wrapper = new Div();
        wrapper.getStyle()
                .display(Display.NONE)
                .overflow(Overflow.AUTO);
        wrapper.add(component);
        configureTab(button, wrapper);

        contentContainer.add(wrapper);
        buttonContainer.insertBefore(tabs.get(index).button(), button);

        Tab tab = new Tab(button, wrapper);
        tab.button()
                .tabbed = this;
        tabs.add(index, tab);
        if (selectedTab >= index) selectedTab++;

        for (int i = index + 1; i < tabs.size(); i++)
            tabs.get(i)
                    .button()
                    .index = i;
        updateTabStops();
        return this;
    }

    /**
     * Shows the requested tab content and hides the other wrappers, updating selected-button styling.
     *
     * @param index existing or selected tab index
     */
    public void selectTab(int index) {
        if (index < 0 || index >= tabs.size())
            throw new IndexOutOfBoundsException(index);
        if (index == selectedTab)
            return;

        for (int i = 0; i < tabs.size(); i++) {
            Tab tab = tabs.get(i);
            tab.button()
                    .attribute("aria-selected", String.valueOf(i == index));
            if (i == index) {
                tab.button()
                        .addClassName("tab-selected");
                tab.content()
                        .getStyle()
                        .visibility(Visibility.VISIBLE);
                tab.content()
                        .getStyle()
                        .display(Display.BLOCK);
                continue;
            }
            tab.content()
                    .getStyle()
                    .display(Display.NONE);
            tab.content()
                    .getStyle()
                    .visibility(Visibility.INVISIBLE);
            tab.button()
                    .removeClassName("tab-selected");
        }
        this.selectedTab = index;
        updateTabStops();
    }

    /**
     * Links one real tab button and panel without depending on their DOM attachment order.
     * @param button tab selector
     * @param panel owned content wrapper
     */
    private void configureTab(TabButton button, Div panel) {
        button.attribute("role", "tab");
        button.attribute("aria-selected", "false");
        button.attribute("aria-controls", Integer.toString(panel.getComponentID()));
        panel.attribute("role", "tabpanel");
        panel.attribute("aria-labelledby", Integer.toString(button.getComponentID()));
        panel.attribute("tabindex", "0");
    }

    /**
     * Maintains one enabled tab stop after insertion or selection, including the no-selection state.
     */
    void updateTabStops() {
        int focus = selectedTab;
        if (focus < 0 || !tabs.get(focus).button().isEnabled()) {
            focus = -1;
            for (int index = 0; index < tabs.size(); index++) if (tabs.get(index).button().isEnabled()) { focus = index; break; }
        }
        for (int index = 0; index < tabs.size(); index++) tabs.get(index)
                .button()
                .attribute("tabindex", index == focus ? "0" : "-1");
    }

    /**
     * Activates adjacent enabled tabs on navigation keys without trapping Tab or browser shortcuts.
     * @param button event target
     * @param event typed browser key event
     */
    void keyDown(TabButton button, KeyDownEvent event) {
        if (event.getKey() == null || event.isAltPressed() || event.isMetaPressed() || tabs.isEmpty()) return;
        int direction;
        int target;
        switch (event.getKey()) {
            case ARROW_LEFT -> { direction = -1; target = button.index - 1; }
            case ARROW_RIGHT -> { direction = 1; target = button.index + 1; }
            case HOME -> { direction = 1; target = 0; }
            case END -> { direction = -1; target = tabs.size() - 1; }
            default -> { return; }
        }
        for (int count = 0; count < tabs.size(); count++) {
            target = Math.floorMod(target, tabs.size());
            TabButton next = tabs.get(target)
                    .button();
            if (next.isEnabled()) {
                selectTab(target);
                next.callMethod("focus");
                return;
            }
            target += direction;
        }
    }

    /**
     * Returns the button at the selected index; callers must select a valid tab first.
     * @return the resulting selected tab value
     */
    public TabButton getSelectedTab() {
        return tabs.get(selectedTab)
                .button();
    }

    /**
     * Returns the content container retained by this instance.
     * @return the resulting content container value
     */
    public Div getContentContainer() {
        return contentContainer;
    }

    /**
     * Returns the button container retained by this instance.
     * @return the resulting button container value
     */
    public Div getButtonContainer() {
        return buttonContainer;
    }

    @Override
    protected void create() {
        this.getStyle()
                .overflow(Overflow.HIDDEN)
                .border("none")
                .display(Display.FLEX)
                .flexDirection(FlexDirection.COLUMN);

        this.buttonContainer.getStyle()
                .display(Display.FLEX)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.FLEX_START)
                .justifyContent(JustifyContent.FLEX_START)
                .gap("0")
                .overflow(Overflow.AUTO);

        this.contentContainer.getStyle()
                .overflow(Overflow.AUTO);

        this.add(buttonContainer, contentContainer);
    }

    @Override
    protected void destroy() {

    }

}
