package geshra.benchmark;

import geshra.net.web.RouteRegistry;
import geshra.net.web.SessionContext;
import geshra.net.web.Wire;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.TextComponent;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
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
 * Assesses reusable, no-context-between-frames raw DEFLATE on real compact packets emitted by
 * 1,000 native two-cell rows. This is codec CPU/steady-state Java allocation evidence, not an
 * implemented WebSocket extension, complete wire accounting or native compressor-memory test.
 * Initial component creation and packet capture happen before warmup; owned zlib state ends
 * after the trial. No application or authentication messages enter the compression fixture.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class CompressionBenchmark {
    @Param({"1", "6"})
    public int level; // Deflater speed/ratio setting chosen outside measurement.
    private byte[][] packets; // Actual compact DOM frames captured from the production dispatcher.
    private byte[] output; // Reused output large enough for the largest uncompressible frame.
    private Deflater deflater; // Owned native compressor, reset independently for each frame.

    /**
     * Captures real serialized rows, then initializes reusable compression storage.
     */
    @Setup
    public void setup() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.attr(Wire.COMPACT)
                .set(true);
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "compression", channel);
        SessionContext.set(context);
        ArrayList<byte[]> captured = new ArrayList<>();
        try {
            UI ui = context.getUI();
            TextComponent body = new TextComponent(null, "tbody");
            ui.add(body);
            BinaryWebSocketFrame initial;
            while ((initial = channel.readOutbound()) != null) initial.release();
            ui.beginUpdate();
            for (int index = 1; index <= 1000; index++) {
                TextComponent row = new TextComponent(null, "tr");
                row.add(new TextComponent(Integer.toString(index), "td"), new TextComponent("Row " + index, "td"));
                body.add(row);
            }
            ui.endUpdate();
            BinaryWebSocketFrame frame;
            while ((frame = channel.readOutbound()) != null) {
                try {
                    byte[] payload = new byte[frame.content().readableBytes()];
                    frame.content()
                            .getBytes(frame.content().readerIndex(), payload);
                    captured.add(payload);
                } finally { frame.release(); }
            }
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
        packets = captured.toArray(byte[][]::new);
        int largest = 0;
        int originalBytes = 0;
        for (byte[] packet : packets) { largest = Math.max(largest, packet.length); originalBytes += packet.length; }
        output = new byte[largest + largest / 8 + 128];
        deflater = new Deflater(level, true);
        System.out.println("DOM compression fixture level=" + level + " frames=" + packets.length + " originalBytes=" + originalBytes + " compressedBytes=" + compress());
    }

    /**
     * Compresses the complete row group, resetting context per frame without allocating Java buffers.
     * @return compressed payload bytes, consumed by JMH
     */
    @Benchmark
    public int compress() {
        int bytes = 0;
        for (byte[] packet : packets) {
            deflater.reset();
            deflater.setInput(packet);
            deflater.finish();
            bytes += deflater.deflate(output);
            if (!deflater.finished()) throw new IllegalStateException("Compression fixture output bound was insufficient");
        }
        return bytes;
    }

    /**
     * Releases native zlib state after the measured trial.
     */
    @TearDown
    public void close() { deflater.end(); }
}
