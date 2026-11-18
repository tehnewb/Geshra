package geshra.benchmark;

import geshra.event.EventPublisher;
import geshra.event.EventTypes;
import geshra.net.web.ui.css.StyleChangeEvent;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Measures synchronous dispatch with realistic handler work and a reusable event.
 * Listener count is varied to expose fixed routing cost and per-listener scaling.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class EventBenchmark {
    @Param({"0", "1", "8", "32"})
    public int listeners; // Number of independently registered handlers.
    private EventPublisher publisher; // Thread-owned publisher fixture.
    private StyleChangeEvent event; // Sequentially reused dispatch payload.
    private int calls; // Observable handler work consumed after every dispatch.

    /**
     * Installs distinct callback objects before measurement.
     */
    @Setup
    public void setup() {
        publisher = new EventPublisher();
        event = new StyleChangeEvent("color", "red");
        for (int index = 0; index < listeners; index++) {
            int increment = index + 1;
            publisher.register(EventTypes.STYLE_CHANGE, value -> calls += increment);
        }
    }

    /**
     * Publishes one event and returns its accumulated handler work.
     * @return observable callback state
     */
    @Benchmark
    public int publish() {
        publisher.publish(event);
        return calls;
    }
}
