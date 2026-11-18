package geshra.benchmark;

import geshra.net.web.Wire;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Exposes the CPU tradeoff between fixed-width and compact field round trips, including
 * worst-case five-byte IDs. Storage is reused so allocation profiling isolates codec behavior.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class WireBenchmark {
    @Param({"42", "16384", "2147483647"})
    public int value; // Field value selected outside the measured operation.
    private ByteBuf buffer; // Reused thread-owned packet storage.

    /**
     * Allocates the fixture before warmup.
     */
    @Setup
    public void setup() { buffer = Unpooled.buffer(16); }

    /**
     * Writes and reads one fixed-width field.
     * @return decoded value consumed by JMH
     */
    @Benchmark
    public int fixed() {
        buffer.clear();
        buffer.writeInt(value);
        return buffer.readInt();
    }

    /**
     * Writes and reads the equivalent compact field with input validation.
     * @return decoded value consumed by JMH
     */
    @Benchmark
    public int compact() {
        buffer.clear();
        Wire.writeUnsigned(buffer, value);
        return Wire.readUnsigned(buffer);
    }

    /**
     * Releases owned packet storage.
     */
    @TearDown
    public void close() { buffer.release(); }
}
