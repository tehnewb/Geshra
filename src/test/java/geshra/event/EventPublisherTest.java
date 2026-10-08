package geshra.event;

import geshra.net.web.ui.css.StyleChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies event publisher behavior using isolated state and observable outcomes.
 */
class EventPublisherTest {
    /**
     * Verifies that orders handlers by priority then registration order.
     */
    @Test
    void ordersHandlersByPriorityThenRegistrationOrder() {
        EventPublisher publisher = new EventPublisher();
        List<String> calls = new ArrayList<>();
        publisher.register(EventTypes.STYLE_CHANGE, event -> calls.add("first"));
        publisher.register(EventTypes.STYLE_CHANGE, 10, event -> calls.add("high"));
        publisher.register(EventTypes.STYLE_CHANGE, event -> calls.add("second"));
        publisher.register(EventTypes.STYLE_CHANGE, -10, event -> calls.add("low"));
        publisher.publish(new StyleChangeEvent("color", "red"));
        assertThat(calls)
                .containsExactly("high", "first", "second", "low");
    }

    /**
     * Verifies that consumes one dispatch and resets when an event is reused.
     */
    @Test
    void consumesOneDispatchAndResetsWhenAnEventIsReused() {
        EventPublisher publisher = new EventPublisher();
        AtomicInteger calls = new AtomicInteger();
        publisher.register(EventTypes.STYLE_CHANGE, event -> {
            calls.incrementAndGet();
            event.consume();
            event.consume();
        });
        publisher.register(EventTypes.STYLE_CHANGE, event -> calls.addAndGet(100));
        StyleChangeEvent event = new StyleChangeEvent("color", "red");
        publisher.publish(event);
        publisher.publish(event);
        assertThat(calls.get())
                .isEqualTo(2);
        assertThat(event.isConsumed())
                .isTrue();
        event.unconsume();
        assertThat(event.isConsumed())
                .isFalse();
    }

    /**
     * Verifies that deduplicates and removes exact handler instances.
     */
    @Test
    void deduplicatesAndRemovesExactHandlerInstances() {
        EventPublisher publisher = new EventPublisher();
        EventHandler<StyleChangeEvent> handler = event -> { };
        assertThat(publisher.register(EventTypes.STYLE_CHANGE, handler))
                .isTrue();
        assertThat(publisher.register(EventTypes.STYLE_CHANGE, 100, handler))
                .isFalse();
        assertThat(publisher.listenerCount(EventTypes.STYLE_CHANGE))
                .isEqualTo(1);
        assertThat(publisher.unregister(EventTypes.STYLE_CHANGE, handler))
                .isTrue();
        assertThat(publisher.unregister(EventTypes.STYLE_CHANGE, handler))
                .isFalse();
        assertThat(publisher.isRegistered(EventTypes.STYLE_CHANGE))
                .isFalse();
        publisher.register(EventTypes.STYLE_CHANGE, handler);
        publisher.clear();
        assertThat(publisher.listenerCount(EventTypes.STYLE_CHANGE))
                .isZero();
    }

    /**
     * Verifies that mutations during dispatch apply to the next publication.
     */
    @Test
    void mutationsDuringDispatchApplyToTheNextPublication() {
        EventPublisher publisher = new EventPublisher();
        List<String> calls = new ArrayList<>();
        EventHandler<StyleChangeEvent> second = event -> calls.add("second");
        EventHandler<StyleChangeEvent> replacement = event -> calls.add("replacement");
        publisher.register(EventTypes.STYLE_CHANGE, event -> {
            calls.add("first");
            publisher.unregister(EventTypes.STYLE_CHANGE, second);
            publisher.register(EventTypes.STYLE_CHANGE, replacement);
        });
        publisher.register(EventTypes.STYLE_CHANGE, second);
        publisher.publish(new StyleChangeEvent("color", "red"));
        publisher.publish(new StyleChangeEvent("color", "blue"));
        assertThat(calls)
                .containsExactly("first", "second", "first", "replacement");
    }

    /**
     * Verifies that routes only to the selected type and propagates handler failures.
     */
    @Test
    void routesOnlyToTheSelectedTypeAndPropagatesHandlerFailures() {
        EventPublisher publisher = new EventPublisher();
        publisher.register(EventTypes.CLICK, event -> { throw new AssertionError("Wrong route"); });
        publisher.publish(new StyleChangeEvent("color", "red"));
        IllegalStateException failure = new IllegalStateException("Handler failed");
        publisher.register(EventTypes.STYLE_CHANGE, event -> { throw failure; });
        assertThatThrownBy(() -> publisher.publish(new StyleChangeEvent("color", "blue")))
                .isSameAs(failure);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new EventPublisher(0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> publisher.register(
                new EventType<StyleChangeEvent>(EventTypes.COUNT, "outside"), event -> { }));
    }

    /**
     * Verifies that publishes independent events while registrations change.
     */
    @Test
    void publishesIndependentEventsWhileRegistrationsChange() throws Exception {
        EventPublisher publisher = new EventPublisher();
        AtomicInteger calls = new AtomicInteger();
        publisher.register(EventTypes.STYLE_CHANGE, event -> calls.incrementAndGet());
        EventHandler<StyleChangeEvent> temporary = event -> { };
        try (var executor = Executors.newFixedThreadPool(3)) {
            Runnable publish = () -> {
                for (int i = 0; i < 1000; i++) {
                    publisher.publish(new StyleChangeEvent("color", "red"));
                }
            };
            var first = executor.submit(publish);
            var second = executor.submit(publish);
            var mutations = executor.submit(() -> {
                for (int i = 0; i < 1000; i++) {
                    publisher.register(EventTypes.STYLE_CHANGE, temporary);
                    publisher.unregister(EventTypes.STYLE_CHANGE, temporary);
                }
            });
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            mutations.get(5, TimeUnit.SECONDS);
        }
        assertThat(calls.get())
                .isEqualTo(2000);
    }
}
