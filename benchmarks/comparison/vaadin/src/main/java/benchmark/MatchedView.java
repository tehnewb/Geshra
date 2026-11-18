package benchmark;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Server-owned native counter/form/table fixture equivalent to Geshra's benchmark session.
 * Uses Flow's Element API rather than themed widgets, grids, templates or injected client
 * implementations. All 1,000 rows exist in the DOM; every button action executes on the server.
 */
@Route("")
@PageTitle("Matched benchmark")
@Tag("main")
public final class MatchedView extends Component {
    private final Element body = new Element("tbody"); // Unvirtualized row parent.
    private final Element counter = text("p", "Counter: 0"); // Server-owned counter display.
    private final Element echo = text("p", "Echo: "); // Last server-acknowledged form value.
    private final Element status = text("p", "Ready"); // Completion description.
    private final Element input = new Element("input"); // Eager native value synchronization.
    private final Element[] rows = new Element[1000]; // Rows indexed by identifier minus one.
    private final Element[] labels = new Element[1000]; // Mutable label cells.
    private int count; // Authoritative server counter.
    private int revision; // Completion sequence shared by the browser harness.

    /**
     * Creates the same native tags, labels and listeners as the other matched applications.
     */
    public MatchedView() {
        getElement().setAttribute("data-ready", "true");
        getElement().setAttribute("data-revision", "0");
        counter.setAttribute("data-counter", "true");
        echo.setAttribute("data-echo", "true");
        status.setAttribute("data-status", "true");
        input.setAttribute("type", "text");
        input.setAttribute("aria-label", "Name");
        input.addPropertyChangeListener("value", "input", event -> { });
        Element controls = new Element("div");
        controls.appendChild(button("Increment", this::increment), input, button("Echo", this::echo), button("Create 1000", this::createRows), button("Update tenth", this::updateRows), button("Select 500", this::selectRow), button("Remove 500", this::removeRow), button("Clear", this::clearRows));
        Element table = new Element("table");
        table.appendChild(body);
        getElement().appendChild(text("h1", "Matched benchmark"), controls, counter, echo, status, table);
    }

    /**
     * Creates one native text element without custom widgets.
     * @param tag native tag
     * @param value exact displayed text
     * @return unattached native element
     */
    private static Element text(String tag, String value) { return new Element(tag).setText(value); }

    /**
     * Creates a server callback whose final revision marks completion for the harness.
     * @param name visible button name
     * @param action authoritative server operation
     * @return native button
     */
    private Element button(String name, Runnable action) {
        Element button = text("button", name);
        button.addEventListener("click", event -> { action.run(); getElement().setAttribute("data-revision", Integer.toString(++revision)); });
        return button;
    }

    /**
     * Increments the authoritative counter.
     */
    private void increment() { counter.setText("Counter: " + ++count); status.setText("Incremented"); }

    /**
     * Reads the native input value synchronized to the server by Flow.
     */
    private void echo() { echo.setText("Echo: " + input.getProperty("value", "")); status.setText("Echoed"); }

    /**
     * Creates exactly 1,000 stable native rows with the matched values.
     */
    private void createRows() {
        body.removeAllChildren();
        for (int index = 0; index < rows.length; index++) {
            Element row = new Element("tr");
            row.setAttribute("data-row", Integer.toString(index + 1));
            Element label = text("td", "Row " + (index + 1));
            row.appendChild(text("td", Integer.toString(index + 1)), label);
            body.appendChild(row);
            rows[index] = row;
            labels[index] = label;
        }
        status.setText("Created 1000");
    }

    /**
     * Updates exactly identifiers 1,11,...,991, preserving other cells and row identity.
     */
    private void updateRows() {
        for (int index = 0; index < labels.length; index += 10) if (labels[index] != null) labels[index].setText(labels[index].getText() + " !!!");
        status.setText("Updated tenth");
    }

    /**
     * Selects identifier 500 with the same CSS class.
     */
    private void selectRow() { if (rows[499] != null) rows[499].setAttribute("class", "selected"); status.setText("Selected 500"); }

    /**
     * Removes identifier 500 from the server-owned row collection.
     */
    private void removeRow() { if (rows[499] != null) body.removeChild(rows[499]); rows[499] = null; labels[499] = null; status.setText("Removed 500"); }

    /**
     * Releases the complete dataset while preserving controls and server counter state.
     */
    private void clearRows() {
        body.removeAllChildren();
        for (int index = 0; index < rows.length; index++) { rows[index] = null; labels[index] = null; }
        status.setText("Cleared");
    }
}
