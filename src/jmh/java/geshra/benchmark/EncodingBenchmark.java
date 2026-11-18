package geshra.benchmark;

import geshra.net.web.ui.DOMUpdate;
import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

/**
 * Measures legacy mutation encoding across ASCII, Unicode, and structural payloads.
 * Each thread owns reusable storage. The writer index is consumed by JMH so encoding cannot
 * be eliminated; GC profiling reports encoder allocations independently of fixture creation.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class EncodingBenchmark {
    @Param({"text", "unicode", "structure", "style"})
    public String workload; // Mutation shape selected outside the measurement loop.
    private DOMUpdate update; // Immutable measured mutation.
    private ByteBuf buffer; // Reused thread-owned encoding destination.

    /**
     * Creates one representative mutation and a reusable heap buffer per benchmark thread.
     */
    @Setup(Level.Trial)
    public void setup() {
        buffer = Unpooled.buffer(1024);
        update = switch (workload) {
            case "unicode" -> new DOMUpdate(DOMUpdateType.SET_TEXT, 42).param(DOMUpdateParam.TEXT, "Café 東京 😀");
            case "structure" -> new DOMUpdate(DOMUpdateType.APPEND_CHILD, 42).param(DOMUpdateParam.IDENTIFIER, 43).param(DOMUpdateParam.HTML, "div");
            case "style" -> new DOMUpdate(DOMUpdateType.SET_STYLE, 42).param(DOMUpdateParam.STYLE_PROPERTY, "display").param(DOMUpdateParam.STYLE_VALUE, "none");
            default -> new DOMUpdate(DOMUpdateType.SET_TEXT, 42).param(DOMUpdateParam.TEXT, "benchmark");
        };
    }

    /**
     * Encodes one mutation using the original fixed-width wire format.
     * @return encoded byte count, consumed by JMH
     */
    @Benchmark
    public int legacy() {
        buffer.clear();
        update.writeTo(buffer);
        return buffer.writerIndex();
    }

    /**
     * Encodes the same mutation using compact identifiers and parameter headers.
     * @return encoded byte count, consumed by JMH
     */
    @Benchmark
    public int compact() {
        buffer.clear();
        update.writeCompactTo(buffer);
        return buffer.writerIndex();
    }

    /**
     * Releases the fixture's owned Netty buffer after all iterations.
     */
    @TearDown(Level.Trial)
    public void close() { buffer.release(); }
}
