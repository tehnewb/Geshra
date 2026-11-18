package com.example.demo.showcase;

import geshra.net.web.ui.components.*;

/**
 * Real native-select regression fixtures with independent Java read-back and callback counts.
 * Each factory call is session-owned; explicit assignment cases remain distinct from untouched
 * defaults so a demo workaround cannot be mistaken for a product fix.
 */
public final class SelectRegressions {
    /**
     * Prevents construction of this stateless fixture factory.
     */
    private SelectRegressions() { }

    /**
     * Builds grouped, assigned, disabled, empty and later-populated selection cases.
     * @return newly allocated fixture section
     */
    public static Section create() {
        /*
         * Every initial state is authored through the public Java API, not browser-side replacement.
         */
        Section section = ShowcaseSamples.section("Select regression cases");
        section.add(fixture("grouped-default", grouped()));
        Select assigned = grouped();
        assigned.setValue("javascript");
        section.add(fixture("explicit-existing", assigned));
        Select missing = grouped();
        missing.setValue("missing");
        section.add(fixture("explicit-missing", missing));
        Select empty = grouped();
        empty.setValue("");
        section.add(fixture("explicit-empty", empty));
        Select selected = new Select();
        selected.add(new OptionGroup("Languages", new Option("java", "Java"), new Option("javascript", "JavaScript").setSelected(true)), new Option("", "Empty value"));
        section.add(fixture("selected-option", selected));
        Option disabled = new Option("disabled", "Disabled first option");
        disabled.setEnabled(false);
        Select disabledOption = new Select();
        disabledOption.add(disabled, new Option("enabled", "Enabled option"));
        section.add(fixture("disabled-first", disabledOption));
        OptionGroup disabledGroup = new OptionGroup("Disabled group", new Option("disabled", "Disabled group option"));
        disabledGroup.setEnabled(false);
        Select disabledGroups = new Select();
        disabledGroups.add(disabledGroup, new OptionGroup("Enabled group", new Option("enabled", "Enabled group option")), new Option("", "Empty value"));
        section.add(fixture("disabled-group", disabledGroups));
        Option only = new Option("disabled", "Only disabled option");
        only.setEnabled(false);
        Select allDisabled = new Select();
        allDisabled.add(only);
        section.add(fixture("all-disabled", allDisabled));
        Select listbox = grouped();
        listbox.setProperty("size", 4);
        section.add(fixture("listbox-default", listbox));
        Select multiple = grouped();
        multiple.setProperty("multiple", true);
        section.add(fixture("multiple-default", multiple));
        Select property = grouped();
        property.setProperty("value", "javascript");
        section.add(fixture("explicit-property", property));
        Select multipleProperty = grouped();
        multipleProperty.setProperty("multiple", true);
        multipleProperty.setProperty("value", "javascript");
        section.add(fixture("multiple-property", multipleProperty));
        Select multipleIndex = grouped();
        multipleIndex.setProperty("multiple", true);
        multipleIndex.setProperty("selectedIndex", 1);
        section.add(fixture("multiple-index", multipleIndex));
        Select later = new Select();
        Div laterFixture = fixture("empty-then-insert", later);
        laterFixture.add(ShowcaseSamples.action("Populate empty select", () -> later.add(new OptionGroup("Later group", new Option("java", "Java"), new Option("javascript", "JavaScript")), new Option("", "Empty value"))));
        section.add(laterFixture);
        Select preserve = grouped();
        Div preserveFixture = fixture("later-insertion", preserve);
        preserveFixture.add(ShowcaseSamples.action("Append another option", () -> preserve.addOption("later", "Later option")));
        preserveFixture.add(ShowcaseSamples.action("Assign JavaScript silently", () -> preserve.setValue("javascript")));
        section.add(preserveFixture);
        return section;
    }

    /**
     * Constructs the original grouped-default failure with no explicit assignment.
     * @return untouched native single select
     */
    private static Select grouped() {
        /*
         * A direct empty option follows the option group to expose browser construction ordering.
         */
        Select select = new Select();
        select.add(new OptionGroup("Languages", new Option("java", "Java"), new Option("javascript", "JavaScript")), new Option("", "Empty value"));
        return select;
    }

    /**
     * Wraps a select with visible server values and a count of actual user input callbacks.
     * @param name stable regression selector
     * @param select real control under test
     * @return fixture wrapper
     */
    private static Div fixture(String name, Select select) {
        /*
         * Reading a value does not create an input event, allowing tests to distinguish silent sync.
         */
        select.ariaLabel(name);
        Paragraph state = new Paragraph("Value not read");
        int[] callbacks = new int[1];
        select.addValueChangeListener(event -> {
            callbacks[0]++;
            state.setText("Java value: " + select.getValue() + "; callbacks: " + callbacks[0]);
        });
        Div fixture = ShowcaseSamples.sample("Select", name, new Label(name, select), select, ShowcaseSamples.action("Read " + name, () -> state.setText("Java value: " + select.getValue() + "; callbacks: " + callbacks[0])), state);
        fixture.attribute("data-regression", name);
        state.attribute("data-regression-feedback", name);
        return fixture;
    }
}
