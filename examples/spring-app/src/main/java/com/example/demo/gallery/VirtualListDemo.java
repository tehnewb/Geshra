package com.example.demo.gallery;

import geshra.net.web.ui.components.*;
import geshra.net.web.ui.components.list.VirtualList;

/**
 * Lazy access to a million rows while rendering only a small visible window.
 * Each call creates a section owned by the current browser session.
 */
public final class VirtualListDemo {
    /**
     * Prevents construction of this stateless example factory.
     */
    private VirtualListDemo() {
    }

    /**
     * Creates the interactive example and its explanatory content.
     *
     * @return a new section ready to add to a route
     */
    public static Section create() {
        /*
         * Generate each requested item by index instead of storing a million strings or components.
         */
        VirtualList<String> virtual = new VirtualList<>(1_000_000, index -> "Item " + index, Span::new);
        // Fixed row heights let the list calculate the visible window without measuring each row.
        virtual.setHeightPixels(256);
        virtual.setRowHeight(32);
        virtual.ariaLabel("Virtual dataset");
        Button last = new Button("Jump to last item");
        last.addClickListener(event -> virtual.scrollToIndex(999_999));
        Button refresh = new Button("Refresh virtual list");
        // Refresh asks the provider for the current window again after its data changes.
        refresh.addClickListener(event -> virtual.refresh());

        return new Section(
                new H2("Virtual list"),
                new Paragraph("A million logical rows are generated on demand. Scroll or jump to the last item; only the visible window and overscan need UI components."),
                new ButtonBar(last, refresh),
                virtual
        );
    }
}
