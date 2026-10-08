package geshra.net.web;

import geshra.net.web.auth.Authenticator;
import geshra.net.web.auth.DefaultAuthenticator;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.awt.Desktop;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

/**
 * HTTP/WebSocket server started and stopped with the consuming Spring application.
 */
public final class WebServer implements SmartLifecycle, AutoCloseable {
    /**
     * Logger for server lifecycle and startup failures.
     */
    private static final Logger LOG = LoggerFactory.getLogger(WebServer.class);
    /**
     * Application-wide authenticator shared by browser sessions.
     */
    private static volatile Authenticator authenticator = new DefaultAuthenticator();
    private final PacketHandlerRegistry packets; // Registry of browser protocol packet handlers.
    private final RouteRegistry routes; // Consumer routes borrowed from the test Spring context.
    private final int port; // Configured TCP port; zero requests an ephemeral port.
    private final boolean openBrowser; // Whether startup opens the server URL in the desktop browser.
    private final ResourceLoader resources; // Resolves application and bundled classpath resources.
    private final String staticLocation; // Resource prefix used for application static-file overrides.
    private EventLoopGroup bossGroup; // Owned acceptor event loop, or null while stopped.
    private EventLoopGroup workerGroup; // Owned client event loops, or null while stopped.
    private volatile Channel channel; // Bound server channel, or null while stopped.
    private int staticCacheBytes = StaticFileHandler.DEFAULT_CACHE_BYTES; // Retained packaged-asset budget shared across connections.
    private StaticFileHandler staticFiles; // Shared response handler and server-owned cache.
    private SessionService sessions; // Server-owned session and authentication workers.
    private SessionPolicy sessionPolicy = new SessionPolicy(); // Validated session and public origin settings.
    private final Authenticator serverAuthenticator; // Captured application verifier, isolated from later servers.

    /**
     * Configures an initially stopped server; start acquires channels and event loops, and stop releases them.
     *
     * @param packets browser packet handler registry
     * @param routes application route registry
     * @param port TCP port from zero to 65535; zero selects an ephemeral port
     * @param openBrowser whether to open the desktop browser after binding
     */
    public WebServer(PacketHandlerRegistry packets, RouteRegistry routes, int port, boolean openBrowser) {
        this(packets, routes, port, openBrowser, new DefaultResourceLoader(), "classpath:/web/");
    }

    /**
     * Configures an initially stopped server; start acquires channels and event loops, and stop releases them.
     *
     * @param packets browser packet handler registry
     * @param routes application route registry
     * @param port TCP port from zero to 65535; zero selects an ephemeral port
     * @param openBrowser whether to open the desktop browser after binding
     * @param resources resource loader for application and bundled assets
     * @param staticLocation nonblank application static resource prefix
     */
    public WebServer(PacketHandlerRegistry packets, RouteRegistry routes, int port, boolean openBrowser, ResourceLoader resources, String staticLocation) {
        this.packets = Objects.requireNonNull(packets);
        this.routes = Objects.requireNonNull(routes);
        this.port = port;
        this.openBrowser = openBrowser;
        this.resources = Objects.requireNonNull(resources);
        this.staticLocation = Objects.requireNonNull(staticLocation);
        this.serverAuthenticator = getAuthenticator();
    }

    /**
     * Configures sessions and the public SEO origin before startup.
     * @param policy validated server policy
     */
    public void setSessionPolicy(SessionPolicy policy) {
        if (isRunning()) throw new IllegalStateException("Configure the session policy before startup");
        sessionPolicy = Objects.requireNonNull(policy);
    }

    @Override
    public synchronized void start() {
        if (isRunning()) return;
        bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("Geshra-Boss"));
        workerGroup = new NioEventLoopGroup(2, new DefaultThreadFactory("Geshra-Worker"));
        try {
            staticFiles = new StaticFileHandler(resources, staticLocation, staticCacheBytes);
            sessions = new SessionService(routes, serverAuthenticator, sessionPolicy);
            PageHttpHandler pages = new PageHttpHandler(routes, sessions, resources, staticLocation);
            channel = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socket) {
                            socket.pipeline()
                                    .addLast(new HttpServerCodec());
                            socket.pipeline()
                                    .addLast(new HttpObjectAggregator(65536));
                            socket.pipeline()
                                    .addLast(new HttpContentCompressor());
                            socket.pipeline()
                                    .addLast(new SessionHttpHandler(sessions));
                            socket.pipeline()
                                    .addLast(new WebSocketServerProtocolHandler("/ws"));
                            socket.pipeline()
                                    .addLast(new WebSocketHandler(routes, packets));
                            socket.pipeline()
                                    .addLast(new ChunkedWriteHandler());
                            socket.pipeline()
                                    .addLast(pages);
                            socket.pipeline()
                                    .addLast(staticFiles);
                        }
                    })
                    .bind(port)
                    .sync()
                    .channel();
            String url = "http://localhost:" + getPort() + "/";
            LOG.info("Geshra web server running at {}", url);
            if (openBrowser && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                try {
                    Desktop.getDesktop()
                            .browse(URI.create(url));
                } catch (Exception e) {
                    LOG.warn("Could not open browser", e);
                }
            }
        } catch (InterruptedException e) {
            stop();
            Thread.currentThread()
                    .interrupt();
            throw new IllegalStateException("Interrupted while starting the geshra web server", e);
        } catch (Exception e) {
            stop();
            throw new IllegalStateException("Could not start the geshra web server on port " + port, e);
        }
    }

    @Override
    public synchronized void stop() {
        SessionContext.invalidateSessions(channel);
        if (sessions != null) { sessions.close(); sessions = null; }
        if (channel != null) {
            channel.close()
                    .syncUninterruptibly();
            channel = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS)
                    .syncUninterruptibly();
            workerGroup = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS)
                    .syncUninterruptibly();
            bossGroup = null;
        }
        if (staticFiles != null) {
            staticFiles.clearCache();
            staticFiles = null;
        }
    }

    /**
     * Sets the packaged-asset payload budget before server startup.
     * @param bytes maximum retained bytes; zero disables caching
     * @throws IllegalStateException if the server is already running
     */
    public synchronized void setStaticCacheBytes(int bytes) {
        if (isRunning()) throw new IllegalStateException("Static cache settings require a stopped server");
        if (bytes < 0) throw new IllegalArgumentException("Static cache budget cannot be negative");
        staticCacheBytes = bytes;
    }

    @Override
    public boolean isRunning() {
        return channel != null && channel.isActive();
    }
    @Override
    public void close() {
        stop();
    }

    /**
     * Returns the bound port, including the assigned port when configured with zero.
     */
    public int getPort() {
        Channel current = channel;
        return current == null ? port : ((InetSocketAddress) current.localAddress()).getPort();
    }

    /**
     * Starts the server and blocks until it is closed, for callers outside Spring.
     */
    public void run(String... args) {
        start();
        Channel current = channel;
        try {
            if (current != null) current.closeFuture()
                    .sync();
        } catch (InterruptedException e) {
            Thread.currentThread()
                    .interrupt();
        } finally {
            stop();
        }
    }

    /**
     * Publishes the non-null application authenticator used by browser sessions.
     *
     * @param value application value or CSS text to retain
     */
    public static void setAuthenticator(Authenticator value) {
        /*
         * Publish one validated reference; readers obtain it through the volatile field without acquiring a lock.
         */
        authenticator = Objects.requireNonNull(value); }
    /**
     * Returns the currently published application authenticator.
     * @return the resulting authenticator value
     */
    public static Authenticator getAuthenticator() {
        /*
         * Read the volatile reference once so concurrent replacement is visible to subsequent sessions.
         */
        return authenticator; }
}
