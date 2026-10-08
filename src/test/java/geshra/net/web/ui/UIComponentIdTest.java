package geshra.net.web.ui;

import geshra.net.web.ui.components.TextComponent;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies uicomponent id behavior using isolated state and observable outcomes.
 */
class UIComponentIdTest {
    /**
     * Verifies that reuses released ids without colliding with active components.
     */
    @Test
    void reusesReleasedIdsWithoutCollidingWithActiveComponents() {
        UI ui = new UI();
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            int id = ui.nextComponentID();
            assertThat(id)
                    .isPositive();
            assertThat(ids.add(id))
                    .isTrue();
            ui.register(id, new TextComponent("Component", "div"));
        }
        for (int id : ids) {
            assertThat(ui.recycle(id))
                    .isNotNull();
            assertThat(ui.recycle(id))
                    .isNull();
        }
        Set<Integer> reused = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            assertThat(reused.add(ui.nextComponentID()))
                    .isTrue();
        }
        assertThat(reused)
                .isEqualTo(ids);
        assertThat(ui.nextComponentID())
                .isEqualTo(65);
    }
}
