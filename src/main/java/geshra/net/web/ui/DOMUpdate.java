package geshra.net.web.ui;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/**
 * One binary DOM mutation. The common one- and two-parameter cases store values directly in the
 * update; larger mutations lazily allocate compact parallel arrays. Parameter insertion order and
 * replacement semantics are preserved without hash-table entries or boxed keys. Updates belong to
 * one UI thread and must not be mutated while their dispatcher is flushing.
 *
 * <p>{@link #writeTo(ByteBuf)} appends directly to a caller-owned packet, avoiding intermediate
 * buffers and UTF-8 byte arrays. Parameter lengths are unsigned 16-bit UTF-8 byte counts. Standalone
 * callers of {@link #encode()} own the returned pooled buffer and must release it.</p>
 */
public class DOMUpdate {
    /**
     * Largest UTF-8 value that the browser protocol can represent.
     */
    private static final int MAX_VALUE_BYTES = 65535;
    private final int componentID; // Target browser component ID.
    private final DOMUpdateType type; // Browser operation and default ordering priority.
    private int priority; // Explicit scheduling priority, never changed by queue insertion.
    private int parameterCount; // Number of distinct parameter keys.
    private DOMUpdateParam firstKey; // Inline first parameter key.
    private Object firstValue; // Inline first parameter value.
    private DOMUpdateParam secondKey; // Inline second parameter key.
    private Object secondValue; // Inline second parameter value.
    private DOMUpdateParam[] extraKeys; // Lazily allocated keys beyond the first two.
    private Object[] extraValues; // Values parallel to extraKeys.

    /**
     * Creates an empty mutation with the operation's default priority.
     * @param type browser operation
     * @param componentID target component ID
     */
    public DOMUpdate(DOMUpdateType type, int componentID) {
        this.type = Objects.requireNonNull(type, "type");
        this.componentID = componentID;
        priority = type.getDefaultPriority();
    }

    /**
     * Stores a stringified value, replacing an existing key without changing its position.
     * @param param parameter key
     * @param value value to stringify now
     * @return this mutation
     */
    public DOMUpdate param(DOMUpdateParam param, Object value) {
        put(param, String.valueOf(value));
        return this;
    }

    /**
     * Changes the explicit ordering priority; lower priorities are encoded first.
     * @param priority ordering priority
     * @return this mutation
     */
    public DOMUpdate priority(int priority) {
        this.priority = priority;
        return this;
    }

    /**
     * Adds values in map iteration order. Values are stringified when encoded, as in the original API.
     * @param parameters parameter values
     * @return this mutation
     */
    public DOMUpdate params(Map<DOMUpdateParam, Object> parameters) {
        for (Map.Entry<DOMUpdateParam, Object> entry : parameters.entrySet()) put(entry.getKey(), entry.getValue());
        return this;
    }

    /**
     * Inserts or replaces a key in compact, insertion-ordered storage.
     * @param key non-null parameter key
     * @param value value retained until encoding
     */
    private void put(DOMUpdateParam key, Object value) {
        Objects.requireNonNull(key, "key");
        if (firstKey == key) { firstValue = value; return; }
        if (secondKey == key) { secondValue = value; return; }
        for (int i = 0; i < parameterCount - 2; i++) {
            if (extraKeys[i] == key) { extraValues[i] = value; return; }
        }
        if (parameterCount == 0) {
            firstKey = key;
            firstValue = value;
        } else if (parameterCount == 1) {
            secondKey = key;
            secondValue = value;
        } else {
            int index = parameterCount - 2;
            if (extraKeys == null) {
                extraKeys = new DOMUpdateParam[4];
                extraValues = new Object[4];
            } else if (index == extraKeys.length) {
                extraKeys = Arrays.copyOf(extraKeys, index * 2);
                extraValues = Arrays.copyOf(extraValues, index * 2);
            }
            extraKeys[index] = key;
            extraValues[index] = value;
        }
        parameterCount++;
    }

    /**
     * Encodes a standalone mutation in an owned pooled buffer.
     * @return encoded mutation; caller must release it
     */
    public ByteBuf encode() {
        ByteBuf buffer = PooledByteBufAllocator.DEFAULT.buffer(64);
        try {
            writeTo(buffer);
            return buffer;
        } catch (RuntimeException | Error failure) {
            buffer.release();
            throw failure;
        }
    }

    /**
     * Appends this mutation to an existing packet without allocating a buffer or byte arrays.
     * Failed encoding restores the original writer index so no partial mutation is exposed.
     * @param buffer caller-owned destination
     * @throws IllegalArgumentException if a UTF-8 parameter exceeds the wire length limit
     */
    public void writeTo(ByteBuf buffer) {
        int start = buffer.writerIndex();
        try {
            buffer.writeByte(type.getCode());
            buffer.writeInt(componentID);
            buffer.writeByte(parameterCount);
            if (parameterCount > 0) writeParameter(buffer, firstKey, firstValue);
            if (parameterCount > 1) writeParameter(buffer, secondKey, secondValue);
            for (int i = 0; i < parameterCount - 2; i++) writeParameter(buffer, extraKeys[i], extraValues[i]);
        } catch (RuntimeException | Error failure) {
            buffer.writerIndex(start);
            throw failure;
        }
    }

    /**
     * Writes a length-prefixed UTF-8 value directly into the destination.
     * @param buffer caller-owned destination
     * @param key parameter key
     * @param value stored parameter value
     */
    private void writeParameter(ByteBuf buffer, DOMUpdateParam key, Object value) {
        String text = String.valueOf(value);
        if (text.length() > MAX_VALUE_BYTES) throw new IllegalArgumentException("DOM parameter exceeds 65535 UTF-8 bytes");
        buffer.writeByte(key.getCode());
        int lengthIndex = buffer.writerIndex();
        buffer.writeShort(0);
        int length = ByteBufUtil.writeUtf8(buffer, text);
        if (length > MAX_VALUE_BYTES) throw new IllegalArgumentException("DOM parameter exceeds 65535 UTF-8 bytes");
        buffer.setShort(lengthIndex, length);
    }

    /**
     * Returns the explicit ordering priority.
     * @return priority
     */
    public int getPriority() { return priority; }

    /**
     * Identifies a queued child creation for removal before an unrendered child moves.
     * @param childID child identifier
     * @return whether this update creates the child
     */
    boolean createsChild(int childID) {
        if (type != DOMUpdateType.APPEND_CHILD && type != DOMUpdateType.INSERT_BEFORE && type != DOMUpdateType.INSERT_AFTER) return false;
        Object value = firstKey == DOMUpdateParam.IDENTIFIER ? firstValue : secondKey == DOMUpdateParam.IDENTIFIER ? secondValue : null;
        if (value == null && extraKeys != null) {
            for (int i = 0; i < parameterCount - 2; i++) {
                if (extraKeys[i] == DOMUpdateParam.IDENTIFIER) value = extraValues[i];
            }
        }
        return value != null && Integer.toString(childID).equals(value.toString());
    }

    /**
     * Returns the target component ID.
     * @return component ID
     */
    public int getComponentID() { return componentID; }

    /**
     * Returns the browser operation.
     * @return operation type
     */
    public DOMUpdateType getType() { return type; }
}
