package geshra.net.web;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Delivers route metadata, public initial HTML, sitemap, robots, and meaningful HTTP statuses.
 * Public page requests allocate no browser sessions or component trees. Static assets continue
 * through the existing bounded asset cache.
 */
@ChannelHandler.Sharable
final class PageHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    /**
     * Removes the existing shell title before injecting a route-specific title.
     */
    private static final Pattern TITLE = Pattern.compile("<title\\b[^>]*>.*?</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /**
     * Case-insensitive shell head insertion point.
     */
    private static final Pattern HEAD_END = Pattern.compile("</head\\s*>", Pattern.CASE_INSENSITIVE);
    /**
     * Case-insensitive shell body insertion point.
     */
    private static final Pattern BODY_END = Pattern.compile("</body\\s*>", Pattern.CASE_INSENSITIVE);
    /**
     * Opening HTML element for document language configuration.
     */
    private static final Pattern HTML_START = Pattern.compile("<html\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    /**
     * Existing HTML language attribute, including quoted and unquoted spellings.
     */
    private static final Pattern LANGUAGE = Pattern.compile("\\s+lang\\s*=\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s>]+)", Pattern.CASE_INSENSITIVE);
    private final RouteRegistry routes; // Application route and authorization index.
    private final SessionService sessions; // Current identity lookup and configured public origin.
    private final String template; // Small packaged application shell read once on startup.
    private final Resource shell; // Original application shell for live filesystem development.
    private final boolean liveShell; // Whether filesystem changes should be reflected immediately.
    private final Resource robotsOverride; // Optional application-owned robots.txt.
    private final Resource sitemapOverride; // Optional application-owned sitemap.xml.

    /**
     * Loads the consumer shell with bundled fallback.
     * @param routes route index
     * @param sessions session service
     * @param resources application resource resolver
     * @param location application asset prefix
     */
    PageHttpHandler(RouteRegistry routes, SessionService sessions, ResourceLoader resources, String location) {
        this.routes = routes;
        this.sessions = sessions;
        Resource shell = resources.getResource((location.endsWith("/") ? location : location + "/") + "index.html");
        if (!shell.exists()) shell = resources.getResource("classpath:/web/index.html");
        this.shell = shell;
        this.liveShell = shell.isFile();
        this.template = readShell(shell);
        String prefix = location.endsWith("/") ? location : location + "/";
        robotsOverride = resources.getResource(prefix + "robots.txt");
        sitemapOverride = resources.getResource(prefix + "sitemap.xml");
    }

    /**
     * Reads a bounded application HTML shell and reports failures explicitly.
     * @param shell shell resource
     * @return UTF-8 template
     */
    private String readShell(Resource shell) {
        try (InputStream input = shell.getInputStream()) {
            byte[] bytes = input.readNBytes(1_048_577);
            if (bytes.length > 1_048_576) throw new IllegalArgumentException("The application HTML shell must be at most one MiB");
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load application HTML shell", failure);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path;
        try {
            path = new QueryStringDecoder(request.uri())
                    .path();
        } catch (IllegalArgumentException invalidPath) {
            ctx.fireChannelRead(request.retain());
            return;
        }
        if (path.contains("/../") || path.endsWith("/..") || path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0 || !request.method().equals(HttpMethod.GET) && !request.method().equals(HttpMethod.HEAD)) {
            ctx.fireChannelRead(request.retain());
            return;
        }
        if (path.equals("/robots.txt")) {
            if (robotsOverride.exists()) { ctx.fireChannelRead(request.retain()); return; }
            String text = "User-agent: *\nAllow: /\nDisallow: /_geshra/\n";
            if (!sessions.publicUrl().isEmpty()) text += "Sitemap: " + sessions.publicUrl() + "/sitemap.xml\n";
            HttpResponses.send(ctx, HttpResponses.create(request, HttpResponseStatus.OK, "text/plain", text));
            return;
        }
        if (path.equals("/sitemap.xml")) {
            if (sitemapOverride.exists()) { ctx.fireChannelRead(request.retain()); return; }
            StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
            if (!sessions.publicUrl().isEmpty()) {
                int count = 0;
                for (Route route : routes.getRoutes()) {
                    if (route.requiresAuthentication() || !route.getAllowedRoles().isEmpty()) continue;
                    for (String item : route.getSitemapPaths()) {
                        if (++count > 50_000) throw new IllegalStateException("A sitemap cannot exceed 50000 URLs");
                        SeoPage page = route.getSeo(routes.normalizeRoutePath(item));
                        if (page != null && !page.isIndexed()) continue;
                        xml.append("<url><loc>")
                                .append(SeoPage.escape(url(sessions.publicUrl(), routes.normalizeRoutePath(item))))
                                .append("</loc></url>");
                    }
                }
            }
            xml.append("</urlset>");
            HttpResponses.send(ctx, HttpResponses.create(request, HttpResponseStatus.OK, "application/xml", xml.toString()));
            return;
        }
        Route route = routes.find(path);
        if (route == null && path.indexOf('.', path.lastIndexOf('/') + 1) >= 0) {
            ctx.fireChannelRead(request.retain());
            return;
        }
        SessionContext session = sessions.find(ctx, request.headers());
        boolean allowed = route == null || routes.canAccess(route, session == null ? null : session.getUser());
        boolean missing = route == null && !path.equals("/");
        boolean protectedPage = route != null && (route.requiresAuthentication() || !route.getAllowedRoles().isEmpty());
        HttpResponseStatus status = !allowed ? session != null && session.isAuthenticated() ? HttpResponseStatus.FORBIDDEN : HttpResponseStatus.UNAUTHORIZED : missing ? HttpResponseStatus.NOT_FOUND : HttpResponseStatus.OK;
        SeoPage page = route == null || !allowed ? null : route.getSeo(routes.normalizeRoutePath(path));
        String html = liveShell ? readShell(shell) : template;
        if (page != null) {
            html = TITLE.matcher(html)
                    .replaceAll("");
            html = HEAD_END.matcher(html)
                    .replaceFirst(Matcher.quoteReplacement(page.head(url(sessions.publicUrl(), routes.normalizeRoutePath(path))) + "</head>"));
            html = BODY_END.matcher(html)
                    .replaceFirst(Matcher.quoteReplacement("<div id=\"geshra-initial-content\">" + page.getContentHtml() + "</div></body>"));
            Matcher opening = HTML_START.matcher(html);
            if (opening.find()) {
                String tag = LANGUAGE.matcher(opening.group())
                        .replaceAll("");
                String replacement = tag.substring(0, tag.length() - 1) + " lang=\"" + SeoPage.escape(page.getLanguage()) + "\">";
                html = opening.replaceFirst(Matcher.quoteReplacement(replacement));
            }
        }
        if (!allowed || missing || protectedPage) html = HEAD_END.matcher(html)
                        .replaceFirst(Matcher.quoteReplacement("<meta data-geshra-seo name=\"robots\" content=\"noindex,nofollow\"></head>"));
        if (!allowed || missing) html = BODY_END.matcher(html)
                .replaceFirst(Matcher.quoteReplacement("<main><h1>" + status.code() + " " + SeoPage.escape(status.reasonPhrase()) + "</h1></main></body>"));
        FullHttpResponse response = HttpResponses.create(request, status, "text/html", html);
        if (!allowed || missing || protectedPage || page != null && !page.isIndexed()) response.headers()
                .set("X-Robots-Tag", "noindex,nofollow");
        HttpResponses.send(ctx, response);
    }

    /**
     * Joins an explicitly configured origin and encoded application path.
     * @param origin configured public origin
     * @param path application route path
     * @return absolute URL, or empty when no public origin is configured
     */
    static String url(String origin, String path) {
        /*
         * URI path encoding preserves question marks and fragments as path data, not URL controls.
         */
        if (origin.isEmpty()) return "";
        try {
            String normalized = path.startsWith("/") ? path : "/" + path;
            return origin + new URI(null, null, normalized, null).getRawPath();
        } catch (URISyntaxException invalidPath) {
            throw new IllegalArgumentException("Invalid sitemap path", invalidPath);
        }
    }
}
