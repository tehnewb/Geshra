package geshra.net.web.ui;

import geshra.net.web.RouteRegistry;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.components.Button;
import geshra.net.web.ui.components.TextField;
import geshra.net.web.ui.css.Style;
import geshra.net.web.ui.event.ClickEvent;
import geshra.net.web.ui.event.ValueChangeEvent;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies component event behavior using isolated state and observable outcomes.
 */
class ComponentEventTest {
    /**
     * Verifies that updates input values before notifying application handlers.
     */
    @Test
    void updatesInputValuesBeforeNotifyingApplicationHandlers() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "value-test", channel);
        SessionContext.set(context);
        try {
            TextField field = new TextField("placeholder", "initial");
            AtomicReference<String> observed = new AtomicReference<>();
            field.addValueChangeListener(event -> observed.set(field.getValue()));
            field.publish(new ValueChangeEvent(field, "initial", "updated"));
            assertThat(field.getValue())
                    .isEqualTo("updated");
            assertThat(observed.get())
                    .isEqualTo("updated");
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Verifies that dispatches component click handlers in priority order.
     */
    @Test
    void dispatchesComponentClickHandlersInPriorityOrder() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext.set(new SessionContext(new RouteRegistry(List.of()), "click-test", channel));
        try {
            Button button = new Button("Click");
            List<String> calls = new ArrayList<>();
            button.addClickListener(event -> calls.add("normal"));
            button.addClickListener(10, event -> calls.add("high"));
            button.publish(new ClickEvent(button, MouseButton.LEFT, 0, 0, 0, 0, 0, 0, 0));
            assertThat(calls)
                    .containsExactly("high", "normal");
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Verifies that publishes style changes and allows events without handlers.
     */
    @Test
    void publishesStyleChangesAndAllowsEventsWithoutHandlers() {
        AtomicReference<String> changed = new AtomicReference<>();
        Style style = new Style(event -> changed.set(event.getValue()), "#example");
        style.set("color", "red");
        assertThat(changed.get())
                .isEqualTo("red");
        Button button = new Button("No handlers");
        ClickEvent click = new ClickEvent(button, MouseButton.LEFT, 0, 0, 0, 0, 0, 0, 0);
        click.consume();
        button.publish(click);
        assertThat(click.isConsumed())
                .isFalse();
    }
}
