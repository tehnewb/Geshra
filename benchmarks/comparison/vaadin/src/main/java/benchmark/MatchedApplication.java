package benchmark;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.ui.Transport;
import com.vaadin.flow.theme.NoTheme;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts production Vaadin Flow with native unthemed HTML and full-duplex WebSocket transport.
 * No commercial component or application theme is used in this matched fixture.
 */
@SpringBootApplication
@NoTheme
@Push(transport = Transport.WEBSOCKET)
public class MatchedApplication implements AppShellConfigurator {
    /**
     * Starts the isolated production comparator.
     * @param arguments Spring server configuration
     */
    public static void main(String[] arguments) { SpringApplication.run(MatchedApplication.class, arguments); }

    @Override
    public void configurePage(AppShellSettings settings) { settings.addLink("stylesheet", "bench.css"); settings.addLink("icon", "data:,"); }
}
