package com.example.demo.gallery;

import geshra.net.web.ui.components.*;

/**
 * Native disclosures, modal dialogs, and popovers with Java event callbacks.
 * Each call creates a section owned by the current browser session.
 */
public final class OverlaysDemo {
    /**
     * Prevents construction of this stateless example factory.
     */
    private OverlaysDemo() {
    }

    /**
     * Creates the interactive example and its explanatory content.
     *
     * @return a new section ready to add to a route
     */
    public static Section create() {
        /*
         * Use native elements so keyboard dismissal and modal focus follow browser behavior.
         */
        Details details = new Details("Disclosure", new Paragraph("Native details content"));
        Paragraph toggle = new Paragraph("Disclosure closed");
        // Listen to a native DOM event instead of recreating disclosure behavior in Java.
        details.listen("toggle", event -> toggle.setText("Disclosure " + event.getValue()));
        Button dismiss = new Button("Close dialog");
        Dialog dialog = new Dialog(new H2("Native dialog"), new Paragraph("Escape cancels this modal using the browser's native behavior."), dismiss);
        // The close event exposes the return value supplied here.
        dismiss.addClickListener(event -> dialog.close("accepted"));
        Button open = new Button("Open dialog");
        // Show the modal after it has been attached to the rendered page.
        open.addClickListener(event -> dialog.showModal());
        Paragraph result = new Paragraph("No dialog result");
        dialog.listen("close", event -> result.setText("Dialog result: " + dialog.getReturnValue()));
        Popover popover = new Popover(new Paragraph("Native popover content"));
        Button showPopover = new Button("Show popover");
        showPopover.addClickListener(event -> popover.show());

        return new Section(
                new H2("Disclosure and overlays"),
                new Paragraph("Open the disclosure or dialog. Escape closes the native modal; clicking outside dismisses the popover."),
                details,
                toggle,
                new Accordion(new Details("First section", new Paragraph("First")), new Details("Second section", new Paragraph("Second"))),
                new ButtonBar(open, showPopover),
                dialog,
                popover,
                result
        );
    }
}
