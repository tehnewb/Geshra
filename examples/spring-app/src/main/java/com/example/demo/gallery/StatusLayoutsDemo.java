package com.example.demo.gallery;

import geshra.net.web.ui.components.*;

/**
 * Small examples of status indicators and reusable layout containers.
 * Each call creates a section owned by the current browser session.
 */
public final class StatusLayoutsDemo {
    /**
     * Prevents construction of this stateless example factory.
     */
    private StatusLayoutsDemo() {
    }

    /**
     * Creates the interactive example and its explanatory content.
     *
     * @return a new section ready to add to a route
     */
    public static Section create() {
        /*
         * Combine independent components without sharing mutable UI state between sessions.
         */
        return new Section(
                new H2("Status and layout"),
                new Paragraph("Compose status indicators and containers with ordinary Java components."),
                new HorizontalLayout(new Badge("New"), new Spinner(), new Progress(60, 100), new Meter(7, 10)),
                new Alert("Accessible alert"),
                new Card(new H3("Card"), new Paragraph("Retained content")),
                new Panel(new Paragraph("Panel")),
                new Divider()
        );
    }
}
