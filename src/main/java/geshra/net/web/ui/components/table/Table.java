package geshra.net.web.ui.components.table;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;
import geshra.net.web.ui.css.FlexGrow;
import geshra.net.web.ui.css.Overflow;
import java.util.ArrayDeque;
import java.util.Collection;

/**
 * Table
 * <p>
 * Represents an HTML {@code <table>} element with separate header ({@code <thead>}) and body ({@code <tbody>}) sections.
 * Provides methods to set column headers, append rows of cells, and clear the body.
 *
 * @author Albert
 * @version 1.0
 * @since May 17, 2025
 */
@SuppressWarnings("unchecked")
public class Table<T> extends Component {

    private final TableHead tableHead; // The table header container ( element).

    private final TableBody tableBody; // The table body container ( element).

    private TableColumn[] columns; // Configured headings; null until columns are initialized.

    private TableSelectionMode selectionMode; // Policy controlling allowed selection cardinality.
    private ArrayDeque<TableRow<T>> selection = new ArrayDeque<>(); // Selected entries in selection order.
    private RowPopulator<T> populator; // Cell factory; null until object row population is configured.

    /**
     * Constructs a new Table component rendered as a {@code <table>} element,
     * with an empty {@code <thead>} and {@code <tbody>} ready for content.
     */
    public Table() {
        super("table");
        this.tableHead = new TableHead();
        this.tableBody = new TableBody();
    }

    /**
     * Lifecycle hook invoked when the component is created.
     * Adds the header and body containers as children of the table.
     */
    @Override
    protected void create() {
        this.add(tableHead, tableBody);
    }

    /**
     * Lifecycle hook invoked when the component is destroyed.
     * No additional teardown is required for Table.
     */
    @Override
    protected void destroy() {
        // No cleanup required
    }

    /**
     * Replace the header row with the given column labels.
     * Clears any existing header content, creates a new {@code <tr>} inside {@code <thead>},
     * and populates it with {@code <th>} cells containing the provided labels.
     *
     * @param labels Text for each header cell, in order.
     * @return This Table instance, for method chaining.
     */
    public Table<T> columns(String... labels) {
        this.columns = new TableColumn[labels.length];

        tableHead.clear();

        TableRow<T> header = new TableRow<>(null);
        tableHead.add(header);

        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            TableColumn column = new TableColumn(label);
            columns[i] = column;

            header.add(column);
        }
        return this;
    }

    /**
     * Appends one row per application value using the configured columns and cell factory. Columns and
     * a RowPopulator must be installed first.
     *
     * @param data serialized bytes or application values to process
     * @return this component for chaining
     */
    public Table<T> rows(T... data) {
        if (columns == null)
            throw new NullPointerException("Columns have not been created for the table");
        if (populator == null)
            throw new NullPointerException("A RowPopulator must be set before filling rows with object data");

        for (int rowIndex = 0; rowIndex < data.length; rowIndex++) {
            T item = data[rowIndex];
            TableRow<T> row = new TableRow<>(item);
            for (int columnIndex = 0; columnIndex < columns.length; columnIndex++) {
                Component component = populator.create(columns[columnIndex].getText(), item);
                TableCell cell = new TableCell("");
                cell.add(component);
                row.add(cell);
            }
            tableBody.add(row);
        }
        return this;
    }

    /**
     * Appends one row per application value using the configured columns and cell factory. Columns and
     * a RowPopulator must be installed first.
     *
     * @param data serialized bytes or application values to process
     * @return this component for chaining
     */
    public Table<T> rows(Collection<T> data) {
        for (T item : data) {
            this.rows(item);
        }
        return this;
    }

    /**
     * Appends a row of text cells in label order without using the application cell factory.
     *
     * @param labels labels supplied to this operation
     * @return this component for chaining
     */
    public Table<T> row(String... labels) {
        TableRow<T> row = new TableRow<>(null);
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            row.add(new TableCell(label));
        }
        tableBody.add(row);
        return this;
    }

    /**
     * Remove all rows from the table body.
     * Clears the {@code <tbody>} element of any child {@code <tr>} elements.
     */
    public void clearRows() {
        tableBody.clear();
    }

    /**
     * Returns the selection mode retained by this instance.
     * @return the resulting selection mode value
     */
    public TableSelectionMode getSelectionMode() {
        return selectionMode;
    }

    /**
     * Updates the selection mode used by subsequent operations.
     *
     * @param selectionMode selection policy; must not be null
     */
    public void setSelectionMode(TableSelectionMode selectionMode) {
        this.selectionMode = selectionMode;
    }

    /**
     * Updates the populator used by subsequent operations.
     *
     * @param populator factory used to build cells from column labels and row values
     */
    public void setPopulator(RowPopulator<T> populator) {
        this.populator = populator;
    }
}
