package com.example.demo.showcase;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.components.*;
import geshra.net.web.ui.components.list.ListItem;
import geshra.net.web.ui.components.list.UnorderedList;

/**
 * Demonstrates semantic text, structural containers, status and layout components using their actual
 * Geshra classes. HTML semantics are preserved inside valid parent structures; presentation is supplied
 * by a showcase-only stylesheet and does not redefine the library's component behavior.
 */
public final class ShowcaseStructure {
    /**
     * Prevents construction of this stateless sample factory.
     */
    private ShowcaseStructure() { }

    /**
     * Creates semantic and layout samples for one browser session.
     * @return newly allocated category
     */
    public static Section create() {
        /*
         * Group inline semantics and container families without substituting generic HTML lookalikes.
         */
        Section section = ShowcaseSamples.section("Text, semantics, layouts and status");
        section.add(ShowcaseSamples.sample("H1,H2,H3,H4,H5,H6", "Native heading levels one through six.", new H1("Heading 1"), new H2("Heading 2"), new H3("Heading 3"), new H4("Heading 4"), new H5("Heading 5"), new H6("Heading 6")));
        section.add(ShowcaseSamples.sample("Paragraph,Span,Strong,Emphasis,Small,Mark,Code,Preformatted,Blockquote,TextComponent", "Native text semantics; code and preformatted whitespace remain literal.", new Paragraph("Paragraph text"), new Span("Inline span "), new Strong("Strong "), new Emphasis("Emphasis "), new Small("Small print "), new Mark("Marked text "), new Code("new Button(\"Hello\")"), new Preformatted("Line 1\n    Indented line 2"), new Blockquote("A semantic quotation"), new TextComponent("Concrete base TextComponent", "p")));
        section.add(ShowcaseSamples.sample("Hyperlink", "Open the existing component gallery in a separate tab.", new Hyperlink("Original component gallery", "/components").attribute("target", "_blank")));
        DefinitionList definition = new DefinitionList(new DescriptionTerm(new Span("Geshra")), new DescriptionDetail(new Paragraph("Server-driven Java UI components")));
        section.add(ShowcaseSamples.sample("DefinitionList,DescriptionTerm,DescriptionDetail", "Native description-list semantics.", definition));
        ListItem orderedFirst = new ListItem();
        orderedFirst.add(new Span("First ordered item"));
        ListItem orderedSecond = new ListItem();
        orderedSecond.add(new Span("Second ordered item"));
        ListItem unorderedFirst = new ListItem();
        unorderedFirst.add(new Span("Unordered item"));
        UnorderedList unordered = new UnorderedList();
        unordered.add(unorderedFirst);
        section.add(ShowcaseSamples.sample("OrderedList,UnorderedList,ListItem", "List containers with actual list-item children.", new OrderedList(orderedFirst, orderedSecond), unordered));
        section.add(ShowcaseSamples.sample("Main,Header,Navigation,Article,Section,Aside,Footer,Div", "Semantic containers have visible outlines in this sample; the names describe their native tags.", new Div(new Header(new Paragraph("Header")), new Navigation(new Paragraph("Navigation")), new Main(new Article(new Paragraph("Article")), new Section(new Paragraph("Section")), new Aside(new Paragraph("Aside"))), new Footer(new Paragraph("Footer")))));
        section.add(ShowcaseSamples.sample("HorizontalLayout,VerticalLayout,GridLayout,StackLayout,Spacer,ButtonBar,Toolbar", "Resize the window to inspect wrapping and grid containment. Toolbar actions update feedback.", layouts()));
        Progress progress = new Progress(40, 100);
        progress.ariaLabel("Determinate progress");
        Progress loading = new Progress(0, 100)
                .setIndeterminate();
        loading.ariaLabel("Indeterminate progress");
        Meter meter = new Meter(7, 10)
                .setThresholds(3, 8)
                .setOptimum(7);
        meter.ariaLabel("Capacity meter");
        Spinner spinner = new Spinner();
        Paragraph status = new Paragraph("Ready: 40 percent");
        section.add(ShowcaseSamples.sample("Badge,Spinner,Progress,Meter,Alert,Card,Panel,Divider", "Ready/loading/error states. Advance changes the native numeric indicators; reset restores 40 percent.", new Badge("Ready"), spinner, progress, loading, meter, new Alert("Example error: this is a demonstration message"), new Card(new H4("Card content"), new Paragraph("A reusable card")), new Panel(new Paragraph("Panel content")), new Divider(), new ButtonBar(ShowcaseSamples.action("Complete progress", () -> { progress.setValue(100); status.setText("Complete: 100 percent"); }), ShowcaseSamples.action("Reset progress", () -> { progress.setValue(40); status.setText("Ready: 40 percent"); })), status));
        return section;
    }

    /**
     * Creates distinguishable layout children and live toolbar feedback.
     * @return layout collection
     */
    private static Div layouts() {
        /*
         * Small labels reveal ordering, wrapping and spacing without hiding their native containers.
         */
        Paragraph result = new Paragraph("Toolbar idle");
        return new Div(new HorizontalLayout(new Badge("Horizontal A"), new Spacer(), new Badge("Horizontal B")), new VerticalLayout(new Badge("Vertical A"), new Badge("Vertical B")), new GridLayout(new Badge("Grid A"), new Badge("Grid B"), new Badge("Grid C")), new StackLayout(new Paragraph("Stack A"), new Paragraph("Stack B")), new ButtonBar(ShowcaseSamples.action("Button bar action", () -> result.setText("Button bar clicked"))), new Toolbar(ShowcaseSamples.action("Toolbar action", () -> result.setText("Toolbar clicked"))), result);
    }

    /**
     * Demonstrates a real tooltip node and an explicit described-by relationship.
     * @return accessible tooltip example
     */
    public static Div tooltip() {
        /*
         * Tooltip is a semantic text primitive; show/hide is explicitly controlled by this example.
         */
        Tooltip tooltip = new Tooltip("Helpful tooltip text");
        tooltip.setProperty("hidden", true);
        Button target = new Button("Show tooltip");
        target.attribute("aria-describedby", Integer.toString(tooltip.getComponentID()));
        target.addClickListener(event -> tooltip.setProperty("hidden", false));
        return ShowcaseSamples.sample("Tooltip", "Show and hide the tooltip. The library provides its semantic node; positioning is application-owned.", target, tooltip, ShowcaseSamples.action("Hide tooltip", () -> tooltip.setProperty("hidden", true)));
    }
}
