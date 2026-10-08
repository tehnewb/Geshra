package geshra.net.web.ui.components.tab;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.components.Div;
import geshra.net.web.ui.css.*;

import java.util.ArrayList;

/**
 * Coordinates a row of tab buttons and a container of tab content. Each tab owns a wrapper whose visibility follows the selected index; no tab is selected initially. Add and select tabs on the owning session event loop after binding the component to a UI.
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

        buttonContainer.add(button);
        contentContainer.add(wrapper);

        Tab tab = new Tab(button, wrapper);
        tab.button().tabbed = this;
        tabs.add(tab);
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

        contentContainer.add(wrapper);
        buttonContainer.insertBefore(tabs.get(index).button(), button);

        Tab tab = new Tab(button, wrapper);
        tab.button().tabbed = this;
        tabs.add(index, tab);
        if (selectedTab >= index) selectedTab++;

        for (int i = index + 1; i < tabs.size(); i++)
            tabs.get(i).button().index = i;
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
