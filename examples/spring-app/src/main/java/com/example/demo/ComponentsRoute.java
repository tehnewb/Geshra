package com.example.demo;

import geshra.event.EventHandler;
import geshra.net.web.Route;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.*;
import geshra.net.web.ui.components.list.VirtualList;
import geshra.net.web.ui.event.DomEvent;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Runnable native component gallery, including a million-item lazy virtual list. Application
 * callbacks are ordinary Java, while controls retain their browser validation and keyboard rules.
 */
@Component
public class ComponentsRoute implements Route {
    @Override
    public String getPath() { return "components"; }

    @Override
    public Collection<String> getAllowedRoles() { return List.of(); }

    @Override
    public void load(UI ui) {
        ui.setTitle("Component gallery");
        ui.attribute("style", "max-width:1000px;margin:24px auto;padding:16px;font:16px system-ui");
        Paragraph status = new Paragraph("Changes: 0");
        AtomicInteger changes = new AtomicInteger();
        Checkbox checkbox = new Checkbox(true);
        checkbox.ariaLabel("Checkbox");
        checkbox.addValueChangeListener(event -> status.setText("Changes: " + changes.incrementAndGet()));
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
        MultiSelect multiple = new MultiSelect();
        multiple.addOption("a,b", "Comma");
        multiple.addOption("+", "Plus");
        multiple.addOption("", "Empty");
        multiple.addOption("日本語", "Unicode");
        multiple.setValue(List.of("a,b", "", "日本語"));
        multiple.ariaLabel("Multiple");
        RadioButton first = new RadioButton("gallery-radio", "first");
        first.setChecked(true);
        first.ariaLabel("First radio");
        RadioButton second = new RadioButton("gallery-radio", "second");
        second.ariaLabel("Second radio");
        Paragraph snapshot = new Paragraph("Read the browser-synchronized Java values");
        snapshot.ariaLabel("Java values");
        Button read = new Button("Read Java values");
        read.addClickListener(event -> snapshot.setText("range=" + range.getValue() + ";checkbox=" + checkbox.isChecked() + ";date=" + date.getValue() + ";color=" + color.getValue() + ";select=" + select.getValue() + ";multiple=" + multiple.getValue() + ";first=" + first.isChecked() + ";second=" + second.isChecked()));
        Button assign = new Button("Assign values");
        assign.addClickListener(event -> {
            checkbox.setChecked(false);
            range.setValue(200.0);
            date.setValue("invalid");
            select.setValue("missing");
            multiple.setValue(List.of("+", ""));
        });

        Details details = new Details("Disclosure", new Paragraph("Native details content"));
        Paragraph toggle = new Paragraph("Disclosure closed");
        details.listen("toggle", event -> toggle.setText("Disclosure " + event.getValue()));
        Button dismiss = new Button("Close dialog");
        Dialog dialog = new Dialog(new H2("Native dialog"), new Paragraph("Escape cancels this modal using the browser's native behavior."), dismiss);
        dismiss.addClickListener(event -> dialog.close("accepted"));
        Button open = new Button("Open dialog");
        open.addClickListener(event -> dialog.showModal());
        Paragraph result = new Paragraph("No dialog result");
        dialog.listen("close", event -> result.setText("Dialog result: " + dialog.getReturnValue()));
        Popover popover = new Popover(new Paragraph("Native popover content"));
        Button showPopover = new Button("Show popover");
        showPopover.addClickListener(event -> popover.show());

        TextField retained = new TextField("Move me", "retained");
        retained.ariaLabel("Retained input");
        Div left = new Div(retained);
        Div right = new Div();
        Button move = new Button("Move input");
        move.addClickListener(event -> right.add(retained));
        Button detach = new Button("Detach input");
        detach.addClickListener(event -> retained.detach());
        Button restore = new Button("Restore input");
        restore.addClickListener(event -> left.add(retained));
        Button dispose = new Button("Dispose input");
        dispose.addClickListener(event -> retained.dispose());

        Button removable = new Button("Removable listener");
        AtomicInteger calls = new AtomicInteger();
        Paragraph listenerStatus = new Paragraph("Native calls: 0");
        EventHandler<DomEvent> listener = event -> listenerStatus.setText("Native calls: " + calls.incrementAndGet());
        removable.listen("click", listener);
        Button unsubscribe = new Button("Remove listener");
        unsubscribe.addClickListener(event -> removable.removeListener("click", listener));

        VirtualList<String> virtual = new VirtualList<>(1_000_000, index -> "Item " + index, Span::new);
        virtual.setHeightPixels(256);
        virtual.setRowHeight(32);
        virtual.ariaLabel("Virtual dataset");
        Button last = new Button("Jump to last item");
        last.addClickListener(event -> virtual.scrollToIndex(999_999));
        Button refresh = new Button("Refresh virtual list");
        refresh.addClickListener(event -> virtual.refresh());

        Canvas canvas = new Canvas(160, 80);
        canvas.ariaLabel("Canvas example");
        canvas.setDrawingProperty("fillStyle", "#1565c0");
        canvas.draw("fillRect", 10, 10, 80, 40);
        SvgCircle circle = new SvgCircle();
        circle.attribute("cx", "40");
        circle.attribute("cy", "40");
        circle.attribute("r", "25");
        circle.attribute("fill", "#1565c0");
        Svg svg = new Svg(circle);
        svg.attribute("viewBox", "0 0 80 80");
        svg.attribute("width", "80");
        svg.attribute("height", "80");
        svg.ariaLabel("SVG example");

        ui.add(new H1("Component gallery"), new Paragraph("Native controls and Java callbacks. The virtual list accesses only the visible part of a million-item dataset."));
        ui.add(new H2("Form controls"), new GridLayout(checkbox, range, date, color, select, multiple, first, second, new Switch(), new NumberField(3.5), new SearchField(), new UrlField(), new TelephoneField(), new TimeField(), new DateTimeField(), new MonthField(), new WeekField(), new EmailField(), new PasswordField(), new TextArea("Multiline\ntext").setRows(3)));
        ui.add(new ButtonBar(read, assign), status, snapshot);
        ui.add(new H2("Disclosure and overlays"), details, toggle, new Accordion(new Details("First section", new Paragraph("First")), new Details("Second section", new Paragraph("Second"))), new ButtonBar(open, showPopover), dialog, popover, result);
        ui.add(new H2("Status and layout"), new HorizontalLayout(new Badge("New"), new Spinner(), new Progress(60, 100), new Meter(7, 10)), new Alert("Accessible alert"), new Card(new H3("Card"), new Paragraph("Retained content")), new Panel(new Paragraph("Panel")), new Divider());
        ui.add(new H2("Retained nodes"), new HorizontalLayout(left, right), new ButtonBar(move, detach, restore, dispose), new ButtonBar(removable, unsubscribe), listenerStatus);
        ui.add(new H2("Virtual list"), new ButtonBar(last, refresh), virtual);
        ui.add(new H2("Native graphics"), new HorizontalLayout(canvas, svg));
    }
}
