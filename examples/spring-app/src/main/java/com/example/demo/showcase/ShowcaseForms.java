package com.example.demo.showcase;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.components.*;
import java.util.List;

/**
 * Exercises native form controls with accessible labels and live server-value feedback. Each control
 * is independently identified, with explicit disabled/read-only/invalid states and a real Form submit.
 * Native picker appearance and validation remain browser-owned; no synthetic replacement controls exist.
 */
public final class ShowcaseForms {
    /**
     * Prevents construction of this stateless category factory.
     */
    private ShowcaseForms() { }

    /**
     * Builds the complete native form-control category for one UI session.
     * @return fresh form samples
     */
    public static Section create() {
        /*
         * Initial valid values make native normalization and later edits visible in every browser.
         */
        Section section = ShowcaseSamples.section("Form controls");
        field(section, "TextField", new TextField("Type here", "Hello"));
        field(section, "NativeTextField", new NativeTextField("text", "Native input"));
        field(section, "InputField", new InputField());
        field(section, "SearchField", new SearchField("Search Geshra"));
        field(section, "EmailField", new EmailField("hello@example.com"));
        field(section, "PasswordField", new PasswordField("example-only"));
        field(section, "UrlField", new UrlField("https://example.com"));
        field(section, "TelephoneField", new TelephoneField("+1 555 0100"));
        field(section, "DateField", new DateField("2026-11-18"));
        field(section, "DateTimeField", new DateTimeField("2026-11-18T14:30"));
        field(section, "TimeField", new TimeField("14:30"));
        field(section, "MonthField", new MonthField("2026-11"));
        field(section, "WeekField", new WeekField("2026-W47"));
        field(section, "ColorField", new ColorField("#2563eb"));
        field(section, "NumberField", new NumberField(3.5).anyStep());
        field(section, "RangeField", new RangeField(40));
        field(section, "TextArea", new TextArea("First line\nSecond line").setRows(3));
        field(section, "Checkbox", new Checkbox(true));
        field(section, "Switch", new Switch(true));
        Checkbox mixed = new Checkbox()
                .setIndeterminate(true);
        mixed.ariaLabel("Indeterminate checkbox");
        section.add(ShowcaseSamples.sample("Checkbox", "Mixed/indeterminate state; click to choose.", new Label("Mixed checkbox", mixed), mixed));
        RadioButton first = new RadioButton("showcase-radio", "Alpha");
        first.setChecked(true);
        RadioButton second = new RadioButton("showcase-radio", "Beta");
        Paragraph radioState = new Paragraph("Selected: Alpha");
        first.addValueChangeListener(event -> radioState.setText("Alpha=" + first.isChecked() + "; Beta=" + second.isChecked()));
        second.addValueChangeListener(event -> radioState.setText("Alpha=" + first.isChecked() + "; Beta=" + second.isChecked()));
        section.add(ShowcaseSamples.sample("RadioButton", "One radio remains checked in the shared native group.", new Label("Alpha", first), first, new Label("Beta", second), second, radioState));
        Select select = new Select();
        select.add(new OptionGroup("Languages", new Option("java", "Java"), new Option("javascript", "JavaScript")), new Option("", "Empty value"));
        field(section, "Select,Option,OptionGroup", select);
        MultiSelect multi = new MultiSelect();
        multi.addOption("alpha", "Alpha");
        multi.addOption("a,b", "Comma value");
        multi.addOption("日本語", "Unicode value");
        multi.setValue(List.of("alpha"));
        field(section, "MultiSelect", multi);
        NativeTextField suggestion = new NativeTextField("text");
        DataList data = new DataList("Java", "JavaScript", "Kotlin");
        data.attachTo(suggestion);
        section.add(ShowcaseSamples.sample("DataList", "Type J to inspect native suggestions.", new Label("Language suggestion", suggestion), suggestion, data));
        HiddenField hidden = new HiddenField("hidden-token");
        Paragraph hiddenState = new Paragraph("HiddenField value: hidden-token (intentionally not rendered)");
        section.add(ShowcaseSamples.sample("HiddenField", "A nonvisual native input. Read its Java value through the button.", hidden, ShowcaseSamples.action("Read hidden value", () -> hiddenState.setText("HiddenField value: " + hidden.getValue())), hiddenState));
        TextField disabled = new TextField("Disabled", "Cannot edit");
        disabled.setEnabled(false);
        NativeTextField readOnly = new NativeTextField("text", "Read-only value")
                .setReadOnly(true);
        EmailField invalid = new EmailField("invalid-email");
        invalid.setRequired(true);
        invalid.attribute("aria-invalid", "true");
        invalid.ariaLabel("Invalid email example");
        section.add(ShowcaseSamples.sample("TextField,NativeTextField,EmailField", "Disabled, read-only and invalid states. Validate email to show native browser feedback.", new Label("Disabled text", disabled), disabled, new Label("Read-only text", readOnly), readOnly, new Label("Invalid email", invalid), invalid, ShowcaseSamples.action("Validate email", () -> invalid.callMethod("reportValidity"))));
        section.add(form());
        FileUploader upload = new FileUploader(true);
        upload.setAcceptFileTypes(".txt", "text/plain");
        Paragraph uploaded = new Paragraph("No file uploaded");
        upload.ariaLabel("Upload text files");
        upload.setHandler((name, bytes) -> uploaded.setText("Uploaded " + name + ": " + bytes.length + " bytes"));
        section.add(ShowcaseSamples.sample("FileUploader", "Choose a small text file. Contents stay in this demo callback and are not saved.", upload, uploaded));
        return section;
    }

    /**
     * Adds a labeled native control with visible Java-side event feedback.
     * @param section owning category
     * @param name source type names and accessible label
     * @param control actual native Geshra control
     */
    private static void field(Section section, String name, Component control) {
        /*
         * Value callbacks run after the framework synchronizes browser-owned values.
         */
        control.ariaLabel(name);
        Paragraph value = new Paragraph(control instanceof PasswordField ? "Edit this control to see update confirmation" : "Edit this control to see its Java value");
        value.attribute("data-feedback", name);
        if (control instanceof InputField) control.listen("input", event -> value.setText("Native input event: " + event.getValue()));
        else control.addValueChangeListener(event -> {
            Object next = event.getNewValue();
            if (control instanceof PasswordField) value.setText("Password updated (" + (next == null ? 0 : next.toString().length()) + " characters)");
            else value.setText("Java value: " + next);
        });
        section.add(ShowcaseSamples.sample(name, "Editable native control with server callback feedback.", new Label(name, control), control, value));
    }

    /**
     * Creates a real named form with submit feedback and native required validation.
     * @return form/fieldset sample
     */
    private static Div form() {
        /*
         * Bind values through Form rather than manually emulating submission.
         */
        Form form = new Form();
        NativeTextField name = new NativeTextField("text", "Ada")
                .setRequired(true);
        name.ariaLabel("Form name");
        form.add(new Label("Name", name));
        form.addFormItem("name", name);
        form.addCheckbox("updates", "Receive updates");
        form.addSubmitItem(new Button("Submit showcase form"));
        Output output = new Output("Not submitted");
        form.addSubmitListener(event -> output.setText("Submitted: " + form.getValues()));
        return ShowcaseSamples.sample("Form,Fieldset,Legend,Output,Label", "Change the name/check the box and submit. Empty required name invokes native validation.", new Fieldset(new Legend(new Span("Example form")), form), output);
    }
}
