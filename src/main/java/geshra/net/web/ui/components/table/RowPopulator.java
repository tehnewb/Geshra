package geshra.net.web.ui.components.table;

import geshra.net.web.ui.Component;

/**
 * Creates a table cell component for a column label and one application row value. Invoked once per column when the table appends a row; the table attaches each returned component.
 */
public interface RowPopulator<T> {

    /**
     * Creates an unlocked standalone style using the default selector and no change listener.
     *
     * @param column column supplied to this operation
     * @param data serialized bytes or application values to process
     * @return a new unlocked style without a listener
     */
    Component create(String column, T data);
}
