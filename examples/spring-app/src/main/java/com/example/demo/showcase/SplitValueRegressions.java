package com.example.demo.showcase;

import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;
import geshra.net.web.ui.components.Div;
import geshra.net.web.ui.components.NumberField;
import geshra.net.web.ui.components.Option;
import geshra.net.web.ui.components.Paragraph;
import geshra.net.web.ui.components.Section;
import geshra.net.web.ui.components.Select;
import geshra.net.web.ui.components.ValueComponent;

/**
 * Replays split initialization through separate real server DOM batches. An intermediate invalid
 * native property is silently read back to Java before a delayed INITIAL_VALUE restores the final
 * requested value. Neither delivery overwrites Java through setValue, so stale server state remains
 * observable. This controlled replay does not depend on incidental creation-packet boundaries.
 */
public final class SplitValueRegressions {
    /**
     * Prevents construction of this session-local fixture factory.
     */
    private SplitValueRegressions() { }

    /**
     * Creates select and number controls with separately triggered normalization and final delivery.
     * @return freshly owned regression section
     */
    public static Section create() {
        /*
         * Separate user commands ensure the intermediate readback can be asserted before finalization.
         */
        Select select = new Select();
        select.add(new Option("java", "Java"), new Option("javascript", "JavaScript"));
        select.setValue("javascript");
        NumberField number = new NumberField(42.0);
        Section section = ShowcaseSamples.section("Split value delivery", fixture("split-select", select), fixture("split-number", number));
        section.add(ShowcaseSamples.action("Deliver split normalization", () -> {
            select.setProperty("value", "missing");
            number.setProperty("value", "invalid");
        }));
        section.add(ShowcaseSamples.action("Deliver delayed initial values", () -> {
            select.queueForDispatch(DOMUpdateType.INITIAL_VALUE, DOMUpdateParam.VALUE, "javascript");
            number.queueForDispatch(DOMUpdateType.INITIAL_VALUE, DOMUpdateParam.VALUE, "42");
            select.push();
            number.push();
        }));
        return section;
    }

    /**
     * Exposes actual Java values and callback counts without changing the control's state.
     * @param name stable fixture name
     * @param control native value component owned by the current session
     * @return wrapper with readback action
     */
    private static Div fixture(String name, ValueComponent<?> control) {
        /*
         * Silent normalization must never increment the application input callback counter.
         */
        control.ariaLabel(name);
        int[] callbacks = new int[1];
        control.addValueChangeListener(event -> callbacks[0]++);
        Paragraph state = new Paragraph("Value not read");
        state.attribute("data-regression-feedback", name);
        Div wrapper = new Div(control, ShowcaseSamples.action("Read " + name, () -> state.setText("Java value: " + control.getValue() + "; callbacks: " + callbacks[0])), state);
        wrapper.attribute("data-regression", name);
        return wrapper;
    }
}
