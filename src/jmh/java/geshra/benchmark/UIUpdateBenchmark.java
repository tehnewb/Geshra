package geshra.benchmark;

import geshra.net.web.RouteRegistry;
import geshra.net.web.SessionContext;
import geshra.net.web.Wire;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.TextComponent;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.util.List;
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
 * Measures real component setters, dirty-list collection, batching, serialization, and buffer
 * ownership through an embedded transport. Network and browser costs are measured separately.
 * Both protocols apply exactly the same number of observable component changes per invocation.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class UIUpdateBenchmark {
    @Param({"false", "true"})
    public boolean compact; // Whether the transport opted into compact batches.
    @Param({"1", "32", "256"})
    public int count; // Number of changed components per update group.
    private EmbeddedChannel channel; // Thread-owned transport whose frames are always released.
    private SessionContext context; // Bound UI fixture for this JMH worker.
    private TextComponent[] components; // Pre-attached mutable components.

    /**
     * Creates and drains the initial render outside measured iterations.
     */
    @Setup
    public void setup() {
        channel = new EmbeddedChannel();
        channel.attr(Wire.COMPACT)
                .set(compact);
        context = new SessionContext(new RouteRegistry(List.of()), "benchmark", channel);
        SessionContext.set(context);
        components = new TextComponent[count];
        UI ui = context.getUI();
        ui.beginUpdate();
        for (int index = 0; index < count; index++) {
            components[index] = new TextComponent("initial", "p");
            ui.add(components[index]);
        }
        ui.endUpdate();
        drain();
    }

    /**
     * Applies one group and consumes every emitted frame.
     * @return total encoded payload bytes
     */
    @Benchmark
    public int update() {
        UI ui = context.getUI();
        ui.beginUpdate();
        for (TextComponent component : components) component.setText("benchmark");
        ui.endUpdate();
        return drain();
    }

    /**
     * Releases outbound reference-counted frames while consuming their payload lengths.
     * @return bytes produced by the preceding group
     */
    private int drain() {
        int bytes = 0;
        BinaryWebSocketFrame frame;
        while ((frame = channel.readOutbound()) != null) {
            bytes += frame.content().readableBytes();
            frame.release();
        }
        return bytes;
    }

    /**
     * Clears the thread binding and releases all transport-owned buffers.
     */
    @TearDown
    public void close() {
        SessionContext.clear();
        channel.finishAndReleaseAll();
    }
}
