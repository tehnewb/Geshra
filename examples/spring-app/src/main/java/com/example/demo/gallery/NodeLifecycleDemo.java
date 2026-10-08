package com.example.demo.gallery;

import geshra.net.web.ui.components.*;
import geshra.event.EventHandler;
import geshra.net.web.ui.event.DomEvent;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Moving, detaching, disposing, and unsubscribing existing UI components.
 * Each call creates a section owned by the current browser session.
 */
public final class NodeLifecycleDemo {
    /**
     * Prevents construction of this stateless example factory.
     */
    private NodeLifecycleDemo() {
    }

    /**
     * Creates the interactive example and its explanatory content.
     *
     * @return a new section ready to add to a route
     */
    public static Section create() {
        /*
         * Retain one component instance to demonstrate node identity and listener ownership.
         */
        TextField retained = new TextField("Move me", "retained");
        retained.ariaLabel("Retained input");
        Div left = new Div(retained);
        Div right = new Div();
        Button move = new Button("Move input");
        // Adding an existing component moves its DOM node instead of cloning it.
        move.addClickListener(event -> right.add(retained));
        Button detach = new Button("Detach input");
        // Detach keeps the component alive so it can be added again.
        detach.addClickListener(event -> retained.detach());
        Button restore = new Button("Restore input");
        restore.addClickListener(event -> left.add(retained));
        Button dispose = new Button("Dispose input");
        // Dispose ends the component lifecycle; it cannot be restored afterward.
        dispose.addClickListener(event -> retained.dispose());

        Button removable = new Button("Removable listener");
        AtomicInteger calls = new AtomicInteger();
        Paragraph listenerStatus = new Paragraph("Native calls: 0");
        EventHandler<DomEvent> listener = event -> listenerStatus.setText("Native calls: " + calls.incrementAndGet());
        removable.listen("click", listener);
        Button unsubscribe = new Button("Remove listener");
        // Removal requires the exact callback instance originally registered.
        unsubscribe.addClickListener(event -> removable.removeListener("click", listener));

        return new Section(
                new H2("Retained nodes"),
                new Paragraph("Move or detach the input, then restore it. Dispose permanently releases it. The listener buttons demonstrate unsubscribing the same callback."),
                new HorizontalLayout(left, right),
                new ButtonBar(move, detach, restore, dispose),
                new ButtonBar(removable, unsubscribe),
                listenerStatus
        );
    }
}
