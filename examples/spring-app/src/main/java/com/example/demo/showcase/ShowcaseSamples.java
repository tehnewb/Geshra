package com.example.demo.showcase;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.components.Button;
import geshra.net.web.ui.components.Div;
import geshra.net.web.ui.components.H2;
import geshra.net.web.ui.components.H3;
import geshra.net.web.ui.components.Paragraph;
import geshra.net.web.ui.components.Section;

/**
 * Builds consistently labeled, independently inspectable showcase samples. Factories create fresh
 * session-owned components; data-demo and data-types expose stable coverage selectors without
 * replacing Geshra component IDs. A sample's descriptive text explains native or unsupported states.
 */
public final class ShowcaseSamples {
    /**
     * Prevents construction of this stateless sample factory.
     */
    private ShowcaseSamples() { }

    /**
     * Creates a named category with stable selectors for visual coverage.
     * @param name category heading
     * @param children category contents
     * @return newly allocated section
     */
    public static Section section(String name, Component... children) {
        /*
         * Keep source categories visible rather than hiding unvisited content behind tabs.
         */
        Section section = new Section(new H2(name));
        section.attribute("data-category", name);
        section.add(children);
        return section;
    }

    /**
     * Wraps real components in a named, documented coverage card.
     * @param types comma-separated source type names represented by the sample
     * @param description instructions and relevant limitations
     * @param children actual live components
     * @return new sample card
     */
    public static Div sample(String types, String description, Component... children) {
        /*
         * The wrapper owns only presentation; component lifecycle remains Geshra-owned.
         */
        Div card = new Div(new H3(types), new Paragraph(description));
        card.attribute("class", "showcase-sample");
        card.attribute("data-demo", types.split(",", 2)[0]);
        card.attribute("data-types", types);
        card.add(children);
        return card;
    }

    /**
     * Creates a button whose Java callback performs one visible operation.
     * @param label accessible action label
     * @param action session-local operation
     * @return configured action button
     */
    public static Button action(String label, Runnable action) {
        /*
         * Use real Java event listeners so the demo exercises the production event protocol.
         */
        Button button = new Button(label);
        button.addClickListener(event -> action.run());
        return button;
    }
}
