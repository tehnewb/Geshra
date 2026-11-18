package benchmark;

import geshra.net.web.ui.components.Button;
import geshra.net.web.ui.components.TextComponent;
import geshra.net.web.ui.components.TextField;

/**
 * Owns one benchmark session's authoritative state and native component tree. Actions mutate
 * the same dataset as the Vaadin and API-backed fixtures; revision changes mark completion.
 * Rows are not virtualized. All operations run on Geshra's normal session dispatch thread.
 */
public final class MatchedState {
    private final BenchElement root = new BenchElement("main"); // Session-owned root and completion marker.
    private final BenchElement body = new BenchElement("tbody"); // Unvirtualized row parent.
    private final TextComponent counter = new TextComponent("Counter: 0", "p"); // Authoritative counter display.
    private final TextComponent echo = new TextComponent("Echo: ", "p"); // Last server-acknowledged form result.
    private final TextComponent status = new TextComponent("Ready", "p"); // Operation completion text.
    private final TextField input = new TextField(); // Browser value synchronized by Geshra's native input path.
    private final BenchElement[] rows = new BenchElement[1000]; // Rows indexed by identifier minus one.
    private final TextComponent[] labels = new TextComponent[1000]; // Mutable row label cells.
    private int count; // Authoritative counter value.
    private int revision; // Completion sequence, incremented exactly once per button action.

    /**
     * Creates the complete matched native UI and installs real server-side callbacks.
     */
    public MatchedState() {
        root.attribute("data-ready", "true");
        root.attribute("data-revision", "0");
        counter.attribute("data-counter", "true");
        echo.attribute("data-echo", "true");
        status.attribute("data-status", "true");
        input.ariaLabel("Name");
        BenchElement controls = new BenchElement("div");
        controls.add(button("Increment", this::increment), input, button("Echo", this::echo), button("Create 1000", this::createRows), button("Update tenth", this::updateRows), button("Select 500", this::selectRow), button("Remove 500", this::removeRow), button("Clear", this::clearRows));
        BenchElement table = new BenchElement("table");
        table.add(body);
        root.add(new TextComponent("Matched benchmark", "h1"), controls, counter, echo, status, table);
    }

    /**
     * Exposes this session's root for initial route attachment.
     * @return native main element
     */
    public BenchElement root() { return root; }

    /**
     * Creates a native button whose callback finishes with the shared revision marker.
     * @param name visible button name
     * @param action server operation
     * @return unattached button
     */
    private Button button(String name, Runnable action) {
        Button button = new Button(name);
        button.addClickListener(event -> { action.run(); root.attribute("data-revision", Integer.toString(++revision)); });
        return button;
    }

    /**
     * Advances the authoritative server counter.
     */
    private void increment() { counter.setText("Counter: " + ++count); status.setText("Incremented"); }

    /**
     * Echoes the server's synchronized input value.
     */
    private void echo() { echo.setText("Echo: " + input.getValue()); status.setText("Echoed"); }

    /**
     * Replaces the dataset with exactly 1,000 stable two-cell native rows.
     */
    private void createRows() {
        body.clear();
        for (int index = 0; index < rows.length; index++) {
            BenchElement row = new BenchElement("tr");
            row.attribute("data-row", Integer.toString(index + 1));
            TextComponent label = new TextComponent("Row " + (index + 1), "td");
            row.add(new TextComponent(Integer.toString(index + 1), "td"), label);
            body.add(row);
            rows[index] = row;
            labels[index] = label;
        }
        status.setText("Created 1000");
    }

    /**
     * Appends the exact suffix to every tenth row, starting at identifier one.
     */
    private void updateRows() {
        for (int index = 0; index < labels.length; index += 10) if (labels[index] != null) labels[index].setText(labels[index].getText() + " !!!");
        status.setText("Updated tenth");
    }

    /**
     * Selects identifier 500 using the same class and visible state as all other fixtures.
     */
    private void selectRow() { if (rows[499] != null) rows[499].attribute("class", "selected"); status.setText("Selected 500"); }

    /**
     * Removes identifier 500 from the authoritative dataset and native UI.
     */
    private void removeRow() { if (rows[499] != null) body.remove(rows[499]); rows[499] = null; labels[499] = null; status.setText("Removed 500"); }

    /**
     * Releases all rows and dataset references while preserving the controls.
     */
    private void clearRows() {
        body.clear();
        for (int index = 0; index < rows.length; index++) { rows[index] = null; labels[index] = null; }
        status.setText("Cleared");
    }
}
