package geshra.net.web.ui;

import geshra.net.web.RouteRegistry;
import geshra.net.web.SessionContext;
import geshra.net.web.packets.ValueSyncPacketHandler;
import geshra.net.web.ui.components.Checkbox;
import geshra.net.web.ui.components.Div;
import geshra.net.web.ui.components.MultiSelect;
import geshra.net.web.ui.components.RangeField;
import geshra.net.web.ui.components.Span;
import geshra.net.web.ui.components.list.VirtualList;
import geshra.net.web.ui.event.ValueChangeEvent;
import geshra.net.web.ui.event.ViewportEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises native-value semantics, subtree ownership, and virtual-list complexity through the
 * real session registry. Browser-level integration checks complement these server-side tests.
 */
class NativeComponentsTest {
    /**
     * Nested access preserves its caller's binding, including when application code throws.
     */
    @Test
    void restoresSessionBindingAfterNestedAccess() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "nested-access", channel);
        SessionContext.set(context);
        try {
            SessionContext.access(session -> assertThat(session).isSameAs(context));
            assertThat(SessionContext.get())
                    .isSameAs(context);
            assertThatThrownBy(() -> SessionContext.access(session -> { throw new IllegalStateException("application failure"); }))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(SessionContext.get())
                    .isSameAs(context);
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }
    /**
     * Programmatic values and browser normalization are silent; real input still updates checked.
     */
    @Test
    void distinguishesAssignmentsNormalizationAndUserInput() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "native-values", channel);
        SessionContext.set(context);
        try {
            Checkbox checkbox = new Checkbox();
            AtomicInteger calls = new AtomicInteger();
            checkbox.addValueChangeListener(event -> calls.incrementAndGet());
            checkbox.setChecked(true);
            assertThat(calls.get())
                    .isZero();
            checkbox.publish(new ValueChangeEvent(checkbox, true, false));
            assertThat(checkbox.isChecked())
                    .isFalse();
            assertThat(calls.get())
                    .isEqualTo(1);
            checkbox.setValueAndNotify(true);
            assertThat(calls.get())
                    .isEqualTo(2);
            RangeField range = new RangeField(500);
            range.addValueChangeListener(event -> calls.incrementAndGet());
            context.getUI()
                    .add(range);
            ByteBuf packet = Unpooled.buffer();
            try {
                packet.writeInt(range.getComponentID());
                packet.writeShort(3);
                packet.writeCharSequence("100", StandardCharsets.UTF_8);
                new ValueSyncPacketHandler()
                        .handlePacket(context, packet);
            } finally {
                packet.release();
            }
            assertThat(range.getValue())
                    .isEqualTo(100.0);
            assertThat(calls.get())
                    .isEqualTo(2);
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Lazy data access and overlapping windows preserve rows and reject invalid or stale requests.
     */
    @Test
    void boundsVirtualWorkAndReusesOverlappingRows() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "virtual", channel);
        SessionContext.set(context);
        try {
            AtomicInteger reads = new AtomicInteger();
            AtomicReference<Span> overlap = new AtomicReference<>();
            VirtualList<Integer> list = new VirtualList<>(1_000_000, index -> { reads.incrementAndGet(); return index; }, index -> {
                Span row = new Span("Item " + index);
                if (index == 5) overlap.set(row);
                return row;
            });
            list.setHeightPixels(256);
            context.getUI()
                    .add(list);
            assertThat(reads.get())
                    .isEqualTo(12);
            int retainedID = overlap.get()
                    .getComponentID();
            list.publish(new ViewportEvent(1, 4, 21));
            assertThat(list.getRenderedCount())
                    .isEqualTo(17);
            assertThat(reads.get())
                    .isEqualTo(21);
            assertThat(context.getUI().get(retainedID))
                    .isSameAs(overlap.get());
            list.publish(new ViewportEvent(0, 500, 520));
            list.publish(new ViewportEvent(1, -1, 10));
            list.publish(new ViewportEvent(1, 0, 5000));
            list.publish(new ViewportEvent(1, 999999, 1000001));
            assertThat(reads.get())
                    .isEqualTo(21);
            list.publish(new ViewportEvent(1, 999988, 1000000));
            assertThat(list.getRenderedCount())
                    .isEqualTo(12);
            assertThat(reads.get())
                    .isEqualTo(33);
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Reparenting and detachment retain IDs; disposing the UI releases detached roots as well.
     */
    @Test
    void retainsMovesAndReleasesDetachedRegistrations() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "retained", channel);
        SessionContext.set(context);
        try {
            Span child = new Span("state");
            Div first = new Div(child);
            Div second = new Div();
            context.getUI()
                    .add(first, second);
            int id = child.getComponentID();
            second.add(child);
            assertThat(child.getParent())
                    .isSameAs(second);
            assertThat(child.getComponentID())
                    .isEqualTo(id);
            first.remove(child);
            assertThat(context.getUI().get(id))
                    .isSameAs(child);
            child.detach();
            assertThat(child.isAttached())
                    .isFalse();
            child.setText("detached update");
            first.add(child);
            assertThat(child.isAttached())
                    .isTrue();
            assertThat(child.getComponentID())
                    .isEqualTo(id);
            assertThatThrownBy(() -> child.add(first))
                    .isInstanceOf(IllegalArgumentException.class);
            child.detach();
            context.getUI()
                    .clear();
            assertThat(context.getUI().get(id))
                    .isNull();
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * JSON values retain their types and hostile characters; multiple selection preserves values.
     */
    @Test
    void encodesTypedValuesAndLosslessSelections() {
        assertThat(BrowserJson.encode(new Object[]{"quote\"\n", false, 2, Double.NaN, null}))
                .isEqualTo("[\"quote\\\"\\u000a\",false,2,null,null]");
        assertThat(BrowserJson.encode("\uD800"))
                .isEqualTo("\"\\ud800\"");
        assertThatThrownBy(() -> BrowserJson.encode(Map.of(2, "invalid")))
                .isInstanceOf(IllegalArgumentException.class);
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext.set(new SessionContext(new RouteRegistry(List.of()), "selection", channel));
        try {
            MultiSelect multiple = new MultiSelect();
            List<String> values = List.of("a,b", "+", "", "日本語", "a b");
            assertThat(multiple.deconstruct(multiple.construct(values)))
                    .isEqualTo(values);
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }
}
