package com.example.demo.gallery;

import geshra.net.web.ui.components.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Native form values, browser normalization, and silent Java assignments.
 * Each call creates a section owned by the current browser session.
 */
public final class FormControlsDemo {
    /**
     * Prevents construction of this stateless example factory.
     */
    private FormControlsDemo() {
    }

    /**
     * Creates the interactive example and its explanatory content.
     *
     * @return a new section ready to add to a route
     */
    public static Section create() {
        /*
         * Build fresh controls for each session; callbacks capture only this render's components.
         */
        Paragraph status = new Paragraph("Changes: 0");
        AtomicInteger changes = new AtomicInteger();
        Checkbox checkbox = new Checkbox(true);
        checkbox.ariaLabel("Checkbox");
        checkbox.addValueChangeListener(event -> status.setText("Changes: " + changes.incrementAndGet()));
        // Deliberately exceed the native maximum: the browser normalizes the value.
        RangeField range = new RangeField(500);
        range.ariaLabel("Range");
        DateField date = new DateField("invalid");
        date.ariaLabel("Date");
        ColorField color = new ColorField();
        color.ariaLabel("Color");
        Select select = new Select();
        select.addOption("alpha", "Alpha");
        select.addOption("beta", "Beta");
        select.ariaLabel("Select");
        // Option values remain distinct even when they contain commas, Unicode, or nothing.
        MultiSelect multiple = new MultiSelect();
        multiple.addOption("a,b", "Comma");
        multiple.addOption("+", "Plus");
        multiple.addOption("", "Empty");
        multiple.addOption("日本語", "Unicode");
        multiple.setValue(List.of("a,b", "", "日本語"));
        multiple.ariaLabel("Multiple");
        // A shared group name makes the browser and Java state enforce one selected radio.
        RadioButton first = new RadioButton("gallery-radio", "first");
        first.setChecked(true);
        first.ariaLabel("First radio");
        RadioButton second = new RadioButton("gallery-radio", "second");
        second.ariaLabel("Second radio");
        Paragraph snapshot = new Paragraph("Read the browser-synchronized Java values");
        snapshot.ariaLabel("Java values");
        // Browser input updates these cached Java values before invoking application callbacks.
        Button read = new Button("Read Java values");
        read.addClickListener(event -> {
            String values = "range=" + range.getValue()
                    + ";checkbox=" + checkbox.isChecked()
                    + ";date=" + date.getValue()
                    + ";color=" + color.getValue()
                    + ";select=" + select.getValue()
                    + ";multiple=" + multiple.getValue()
                    + ";first=" + first.isChecked()
                    + ";second=" + second.isChecked();
            snapshot.setText(values);
        });
        // Like assigning input.value in JavaScript, setters do not fire change listeners.
        Button assign = new Button("Assign values");
        assign.addClickListener(event -> {
            checkbox.setChecked(false);
            range.setValue(200.0);
            date.setValue("invalid");
            select.setValue("missing");
            multiple.setValue(List.of("+", ""));
        });

        return new Section(
                new H2("Form controls"),
                new Paragraph("Change the controls, then read their Java values. Assign values demonstrates silent updates and native browser normalization."),
                new GridLayout(
                        checkbox,
                        range,
                        date,
                        color,
                        select,
                        multiple,
                        first,
                        second,
                        new Switch(),
                        new NumberField(3.5),
                        new SearchField(),
                        new UrlField(),
                        new TelephoneField(),
                        new TimeField(),
                        new DateTimeField(),
                        new MonthField(),
                        new WeekField(),
                        new EmailField(),
                        new PasswordField(),
                        new TextArea("Multiline\ntext").setRows(3)
                ),
                new ButtonBar(read, assign),
                status,
                snapshot
        );
    }
}
