package geshra.benchmark;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.DOMDispatcher;
import geshra.net.web.ui.DOMUpdate;
import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;
import geshra.net.web.ui.Key;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.TextComponent;
import geshra.net.web.ui.css.Style;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Measures DOM serialization, UI registry allocation, style construction, and keyboard routing.
 * Each worker owns its mutable state. Frames are read and released so the DOM workload includes
 * buffer lifecycle costs without retaining packets between invocations. The returned values
 * prevent dead-code elimination; JMH's GC profiler reports normalized allocated bytes.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class GeshraBenchmark {
    @Param({"64"})
    public int count; // Number of updates or registered component IDs per invocation.
    private EmbeddedChannel channel; // Local transport avoiding network noise in serialization measurements.
    private Component component; // Shared payload isolating registry allocation from component construction.

    /**
     * Creates transport state outside the measured invocation.
     */
    @Setup(Level.Trial)
    public void setup() {
        channel = new EmbeddedChannel();
        component = new TextComponent("shared", "div");
    }

    /**
     * Releases any remaining transport resources after a trial.
     */
    @TearDown(Level.Trial)
    public void teardown() {
        channel.finishAndReleaseAll();
    }

    /**
     * Creates, encodes, and releases a batch of ordinary text mutations.
     * @return serialized byte count
     */
    @Benchmark
    public int domBatch() {
        DOMDispatcher dispatcher = new DOMDispatcher();
        for (int i = 0; i < count; i++) {
            dispatcher.queue(new DOMUpdate(DOMUpdateType.SET_TEXT, i).param(DOMUpdateParam.TEXT, "A representative label in the browser"));
        }
        dispatcher.flush(channel);
        int bytes = 0;
        BinaryWebSocketFrame frame;
        while ((frame = channel.readOutbound()) != null) {
            bytes += frame.content().readableBytes();
            frame.release();
        }
        return bytes;
    }

    /**
     * Registers dense IDs and reads the final component.
     * @return final component, keeping the registry workload observable
     */
    @Benchmark
    public Component componentRegistry() {
        UI ui = new UI();
        for (int i = 0; i < count; i++) ui.register(ui.nextComponentID(), component);
        return ui.get(count);
    }

    /**
     * Constructs a standalone style and adds two typical properties.
     * @return constructed style
     */
    @Benchmark
    public Style standaloneStyle() {
        return new Style(null, "#example")
                .set("color", "red")
                .set("padding", "8px");
    }

    /**
     * Resolves a commonly used special keyboard key.
     * @return mapped key
     */
    @Benchmark
    public Key keyboardLookup() {
        return Key.fromName("ArrowLeft");
    }
}
