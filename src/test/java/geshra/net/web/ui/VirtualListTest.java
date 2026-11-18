package geshra.net.web.ui;

import geshra.net.web.RouteRegistry;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.components.Span;
import geshra.net.web.ui.components.list.VirtualList;
import geshra.net.web.ui.event.ViewportEvent;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks partial-window consistency, bounded keys and lazy dataset replacement using real UI
 * registration/serialization. Browser tests separately verify outstanding requests and focus.
 */
class VirtualListTest {
    /**
     * A failed provider leaves only successfully inserted indices committed and can recover.
     */
    @Test
    void acknowledgesPartialFailureAndRecoversWithoutSkippingARow() {
        withSession(context -> {
            AtomicBoolean fail = new AtomicBoolean(true);
            VirtualList<Integer> list = new VirtualList<>(1000, index -> {
                if (index == 105 && fail.getAndSet(false)) throw new IllegalStateException("provider failure");
                return index;
            }, index -> new Span("Row " + index));
            context.getUI()
                    .add(list);
            drain(context);
            assertThatThrownBy(() -> list.publish(new ViewportEvent(1, 100, 110)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(list.getRenderedCount())
                    .isEqualTo(5);
            String partial = drain(context);
            assertThat(partial)
                    .contains("\"first\":100", "\"end\":105", "\"requestFirst\":100", "\"requestEnd\":110", "\"failed\":true");
            list.publish(new ViewportEvent(1, 100, 110));
            assertThat(list.getRenderedCount())
                    .isEqualTo(10);
            assertThat(drain(context))
                    .contains("Row 105", "\"failed\":false");
        });
    }

    /**
     * Visible duplicate keys fail without consuming an index or permanently reserving that key.
     */
    @Test
    void rejectsDuplicateVisibleKeysAndReleasesEvictedKeys() {
        withSession(context -> {
            AtomicBoolean duplicate = new AtomicBoolean(true);
            VirtualList<Integer> list = new VirtualList<>(1000, index -> index, index -> new Span("Row " + index));
            list.setKeyProvider(index -> Integer.toString(index == 105 && duplicate.get() ? 104 : index));
            context.getUI()
                    .add(list);
            assertThatThrownBy(() -> list.publish(new ViewportEvent(1, 100, 110)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(list.getRenderedCount())
                    .isEqualTo(5);
            duplicate.set(false);
            list.publish(new ViewportEvent(1, 100, 110));
            list.publish(new ViewportEvent(1, 0, 10));
            list.publish(new ViewportEvent(1, 100, 110));
            assertThat(list.getRenderedCount())
                    .isEqualTo(10);
        });
    }

    /**
     * Throwing content lifecycle hooks release partially registered descendants before retry.
     */
    @Test
    void cleansUpContentWhoseAttachmentFails() {
        withSession(context -> {
            AtomicReference<FailingVirtualContent> failed = new AtomicReference<>();
            VirtualList<Integer> list = new VirtualList<>(1000, index -> index, index -> {
                if (index != 105 || failed.get() != null) return new Span("Row " + index);
                FailingVirtualContent content = new FailingVirtualContent();
                failed.set(content);
                return content;
            });
            list.setKeyProvider(Object::toString);
            context.getUI()
                    .add(list);
            assertThatThrownBy(() -> list.publish(new ViewportEvent(1, 100, 110)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(context.getUI().get(failed.get().getComponentID()))
                    .isNull();
            list.publish(new ViewportEvent(1, 100, 110));
            assertThat(list.getRenderedCount())
                    .isEqualTo(10);
        });
    }

    /**
     * Replacing a lazy source clamps the current window and rejects the previous generation.
     */
    @Test
    void replacesLazyDatasetAndRejectsStaleRequests() {
        withSession(context -> {
            VirtualList<Integer> list = new VirtualList<>(100000, index -> index, index -> new Span("Row " + index));
            assertThatThrownBy(() -> list.setMaximumPendingRequests(0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> list.setMaximumPendingRequests(33))
                    .isInstanceOf(IllegalArgumentException.class);
            context.getUI()
                    .add(list);
            list.publish(new ViewportEvent(1, 99980, 100000));
            list.setDataProvider(3, index -> index + 10);
            assertThat(list.getItemCount())
                    .isEqualTo(3);
            assertThat(list.getRenderedCount())
                    .isEqualTo(3);
            list.publish(new ViewportEvent(1, 99980, 100000));
            assertThat(list.getRenderedCount())
                    .isEqualTo(3);
            list.setDataProvider(0, index -> { throw new AssertionError("empty source accessed"); });
            assertThat(list.getRenderedCount())
                    .isZero();
            list.publish(new ViewportEvent(3, 0, 0));
            assertThat(list.getRenderedCount())
                    .isZero();
        });
    }

    /**
     * Opens and closes the actual session binding and pooled outbound-frame ownership per test.
     * @param test session-bound assertions
     */
    private static void withSession(Consumer<SessionContext> test) {
        /*
         * Cleanup is shared because these checks all exercise the same real session lifecycle.
         */
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "virtual-list-test", channel);
        SessionContext.set(context);
        try {
            test.accept(context);
        } finally {
            context.getUI()
                    .clear();
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Drains real legacy frames, preserving their textual JSON for failure acknowledgment checks.
     * @param context owning embedded session
     * @return raw UTF-8 payloads including binary field delimiters
     */
    private static String drain(SessionContext context) {
        /*
         * JSON is embedded verbatim in the normal legacy packet; every frame is released here.
         */
        EmbeddedChannel channel = (EmbeddedChannel) context.getChannel();
        StringBuilder output = new StringBuilder();
        BinaryWebSocketFrame frame;
        while ((frame = channel.readOutbound()) != null) {
            try {
                output.append(frame.content().toString(StandardCharsets.UTF_8));
            } finally {
                frame.release();
            }
        }
        return output.toString();
    }
}
