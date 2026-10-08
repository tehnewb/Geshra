package geshra.net.web.ui.components.table;

import geshra.net.web.ui.Component;

/**
 * Associates an HTML table row with an application value. Changing the stored value does not recreate cell content; callers update cells separately.
 */
public class TableRow<T> extends Component {

    private T data; // Application value associated with this row.

    /**
     * Creates an unattached table row component; attachment and DOM updates are owned by its session UI.
     *
     * @param data serialized bytes or application values to process
     */
    public TableRow(T data) {
        super("tr");
        this.data = data;
    }

    @Override
    protected void create() {

    }

    @Override
    protected void destroy() {

    }

    /**
     * Returns the data retained by this instance.
     * @return the resulting data value
     */
    public T getData() {
        return data;
    }

    /**
     * Updates the data used by subsequent operations.
     *
     * @param data serialized bytes or application values to process
     */
    public void setData(T data) {
        this.data = data;
    }
}
