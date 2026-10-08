package geshra.net.web.ui;

import java.util.Map;

/**
 * Encodes JSON-compatible browser property values and native method arguments without a runtime
 * JSON dependency. Supports strings, booleans, numbers, arrays, iterables, string-keyed maps, and
 * null. Component arguments resolve to native elements. A depth bound rejects cyclic or excessively
 * nested objects. Non-finite numbers become
 * null, matching JSON.stringify. Unsupported Java objects fail rather than silently changing type.
 */
public final class BrowserJson {
    /**
     * Maximum configuration nesting depth.
     */
    private static final int MAX_DEPTH = 32;

    /**
     * Prevents instances of this stateless browser-wire encoder.
     */
    private BrowserJson() { }

    /**
     * Encodes a JSON-compatible value for JSON.parse in the browser.
     * @param value compatible value
     * @return JSON text
     */
    public static String encode(Object value) {
        /*
         * One builder owns the complete output; recursive writes avoid intermediate JSON strings.
         */
        StringBuilder output = new StringBuilder(64);
        write(output, value, 0);
        return output.toString();
    }

    /**
     * Writes one value recursively into a shared builder.
     * @param output destination
     * @param value compatible value
     * @param depth current nesting depth
     */
    private static void write(StringBuilder output, Object value, int depth) {
        /*
         * A fixed depth guard bounds cycles without allocating an identity set for ordinary values.
         */
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Browser JSON nesting exceeds 32 levels");
        if (value == null) output.append("null");
        else if (value instanceof String text) quote(output, text);
        else if (value instanceof Boolean flag) output.append(flag);
        else if (value instanceof Number number) {
            if (number instanceof Double && !Double.isFinite(number.doubleValue()) || number instanceof Float && !Float.isFinite(number.floatValue())) output.append("null");
            else output.append(number);
        } else if (value instanceof Component component) {
            output.append("{\"$element\":");
            output.append(component.getComponentID());
            output.append('}');
        } else if (value instanceof Object[] array) {
            output.append('[');
            for (int i = 0; i < array.length; i++) {
                if (i != 0) output.append(',');
                write(output, array[i], depth + 1);
            }
            output.append(']');
        } else if (value instanceof Iterable<?> values) {
            output.append('[');
            boolean first = true;
            for (Object item : values) {
                if (!first) output.append(',');
                first = false;
                write(output, item, depth + 1);
            }
            output.append(']');
        } else if (value instanceof Map<?, ?> values) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("Browser object keys must be strings");
                if (!first) output.append(',');
                first = false;
                quote(output, key);
                output.append(':');
                write(output, entry.getValue(), depth + 1);
            }
            output.append('}');
        } else throw new IllegalArgumentException("Unsupported browser value: " + value.getClass().getName());
    }

    /**
     * Writes one escaped JSON string, including lone UTF-16 surrogates.
     * @param output destination
     * @param text source string
     */
    private static void quote(StringBuilder output, String text) {
        /*
         * Escaping UTF-16 units preserves JavaScript string contents even for lone surrogates.
         */
        output.append('"');
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == '"' || character == '\\') output.append('\\')
                    .append(character);
            else if (character < 32 || Character.isSurrogate(character)) {
                output.append("\\u");
                for (int shift = 12; shift >= 0; shift -= 4) output.append(Character.forDigit(character >> shift & 15, 16));
            } else output.append(character);
        }
        output.append('"');
    }
}
