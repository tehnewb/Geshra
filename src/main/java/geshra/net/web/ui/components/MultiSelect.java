package geshra.net.web.ui.components;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Native multiple selection list retaining selected values in document order. Its internal wire
 * format percent-escapes values, so commas, plus signs, Unicode, and empty option values round trip.
 */
public class MultiSelect extends ValueComponent<List<String>> {
    /**
     * Creates an empty multiple select.
     */
    public MultiSelect() { super("select", List.of()); setProperty("multiple", true); }

    /**
     * Appends an option.
     * @param value submitted string
     * @param label visible label
     * @return this selection list
     */
    public MultiSelect addOption(String value, String label) { add(new Option(value, label)); return this; }

    /**
     * Sets the number of visible options.
     * @param rows positive row count
     * @return this selection list
     */
    public MultiSelect setRows(int rows) { if (rows <= 0) throw new IllegalArgumentException("Rows must be positive"); setProperty("size", rows); return this; }

    @Override
    public String construct(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (!result.isEmpty()) result.append(',');
            result.append('v');
            result.append(URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return result.toString();
    }

    @Override
    public List<String> deconstruct(String value) {
        if (value.isEmpty()) return List.of();
        String[] tokens = value.split(",", -1);
        ArrayList<String> result = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (!token.startsWith("v")) throw new IllegalArgumentException("Invalid selection token");
            result.add(URLDecoder.decode(token.substring(1), StandardCharsets.UTF_8));
        }
        return List.copyOf(result);
    }

}
