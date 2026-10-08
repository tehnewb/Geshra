package geshra.spring;

import geshra.net.web.auth.Authenticator;
import geshra.net.web.auth.DefaultAuthenticator;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

import geshra.net.web.*;
import geshra.net.web.packets.*;

/**
 * Automatically installs the web framework into a consumer's Spring Boot application.
 */
@AutoConfiguration
@EnableConfigurationProperties(GeshraWebProperties.class)
@ConditionalOnProperty(prefix = "geshra.web", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GeshraAutoConfiguration {
    /**
     * Builds the route index from consumer Route beans, rejecting duplicate paths.
     *
     * @param routes application route registry
     * @return the resulting route registry value
     */
    @Bean
    @ConditionalOnMissingBean
    public RouteRegistry routeRegistry(List<Route> routes) {
        return new RouteRegistry(routes);
    }

    /**
     * Builds the opcode index from available PacketHandler beans, rejecting duplicate IDs.
     *
     * @param handlers handlers supplied to this operation
     * @return the resulting packet handler registry value
     */
    @Bean
    @ConditionalOnMissingBean
    public PacketHandlerRegistry packetHandlerRegistry(List<PacketHandler> handlers) {
        return new PacketHandlerRegistry(handlers);
    }

    /**
     * Provides the default in-memory authenticator when the consumer has not supplied one.
     * @return the resulting geshra authenticator value
     */
    @Bean
    @ConditionalOnMissingBean(Authenticator.class)
    public DefaultAuthenticator geshraAuthenticator() {
        return new DefaultAuthenticator();
    }

    /**
     * Creates the server managed by the Spring context using bound properties and consumer resource resolution.
     *
     * @param packets browser packet handler registry
     * @param routes application route registry
     * @param properties bound web server settings
     * @param resources resource loader for application and bundled assets
     * @param authenticator application authentication implementation
     * @return the resulting web server value
     */
    @Bean
    @ConditionalOnMissingBean
    public WebServer webServer(PacketHandlerRegistry packets, RouteRegistry routes, GeshraWebProperties properties, ResourceLoader resources, Authenticator authenticator) {
        WebServer.setAuthenticator(authenticator);
        WebServer server = new WebServer(packets, routes, properties.getPort(), properties.isOpenBrowser(), resources, properties.getStaticLocation());
        server.setStaticCacheBytes(properties.getStaticCacheBytes());
        server.setSessionPolicy(new SessionPolicy(properties.getSessionTimeout(), properties.getSessionCookieName(), properties.isSecureCookies(), properties.getPublicUrl()));
        return server;
    }

    /**
     * Provides the cookie acknowledgment decoder for server-verified authentication callbacks.
     * @return authentication completion decoder
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthenticationPacketHandler authenticationPacketHandler() { return new AuthenticationPacketHandler(); }

    /**
     * Provides the route packet decoder when no consumer replacement exists.
     * @return the resulting route packet handler value
     */
    @Bean
    @ConditionalOnMissingBean
    public RoutePacketHandler routePacketHandler() {
        return new RoutePacketHandler();
    }
    /**
     * Provides the mouse packet decoder when no consumer replacement exists.
     * @return the resulting mouse packet handler value
     */
    @Bean
    @ConditionalOnMissingBean
    public MousePacketHandler mousePacketHandler() {
        return new MousePacketHandler();
    }
    /**
     * Provides the key down packet decoder when no consumer replacement exists.
     * @return the resulting key down packet handler value
     */
    @Bean
    @ConditionalOnMissingBean
    public KeyDownPacketHandler keyDownPacketHandler() {
        return new KeyDownPacketHandler();
    }
    /**
     * Provides the key up packet decoder when no consumer replacement exists.
     * @return the resulting key up packet handler value
     */
    @Bean
    @ConditionalOnMissingBean
    public KeyUpPacketHandler keyUpPacketHandler() {
        return new KeyUpPacketHandler();
    }
    /**
     * Provides the value change packet decoder when no consumer replacement exists.
     * @return the resulting value change packet handler value
     */
    @Bean
    @ConditionalOnMissingBean
    public ValueChangePacketHandler valueChangePacketHandler() {
        return new ValueChangePacketHandler();
    }
    /**
     * Provides the submit packet decoder when no consumer replacement exists.
     * @return the resulting submit packet handler value
     */
    @Bean
    @ConditionalOnMissingBean
    public SubmitPacketHandler submitPacketHandler() {
        return new SubmitPacketHandler();
    }
    /**
     * Provides the file upload packet decoder when no consumer replacement exists.
     * @return the resulting file upload packet handler value
     */
    @Bean
    @ConditionalOnMissingBean
    public FileUploadPacketHandler fileUploadPacketHandler() {
        return new FileUploadPacketHandler();
    }
    /**
     * Provides the heartbeat packet decoder when no consumer replacement exists.
     * @return the resulting heartbeat packet handler value
     */
    @Bean
    @ConditionalOnMissingBean
    public HeartbeatPacketHandler heartbeatPacketHandler() {
        return new HeartbeatPacketHandler();
    }

    /**
     * Provides native browser event decoding.
     * @return event decoder
     */
    @Bean
    @ConditionalOnMissingBean
    public DomEventPacketHandler domEventPacketHandler() { return new DomEventPacketHandler(); }

    /**
     * Provides silent browser-normalization synchronization.
     * @return normalization decoder
     */
    @Bean
    @ConditionalOnMissingBean
    public ValueSyncPacketHandler valueSyncPacketHandler() { return new ValueSyncPacketHandler(); }

    /**
     * Provides virtual-list viewport decoding.
     * @return viewport decoder
     */
    @Bean
    @ConditionalOnMissingBean
    public ViewportPacketHandler viewportPacketHandler() { return new ViewportPacketHandler(); }
}
