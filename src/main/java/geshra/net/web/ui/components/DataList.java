package geshra.net.web.ui.components;



import geshra.net.web.ui.Component;

/**
 * Native input suggestion list. Associate an input using its list attribute and this component ID;
 * suggestions remain optional text rather than forcing selection.
 */
public class DataList extends Component {
    /**
     * Creates a native suggestion list.
     * @param suggestions suggestion strings
     */
    public DataList(String... suggestions) { super("datalist"); for (String suggestion : suggestions) add(new Option(suggestion, suggestion)); }

    /**
     * Associates a text input with this native suggestion list.
     * @param input associated input
     * @return this suggestion list
     */
    public DataList attachTo(NativeTextField input) { input.attribute("list", Integer.toString(getComponentID())); return this; }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }

}
