package geshra.net.web;

import geshra.net.web.auth.User;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.H1;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Resolves browser navigation to application routes and applies role checks before loading a session UI. Exact routes take precedence over a leading route segment; unrecognized paths use the configured fallback. Register routes during startup, before concurrent request handling begins.
 */
@Component
public class RouteRegistry {

    private final Map<String, Route> routes = new HashMap<>(); // A mapping of route paths to their corresponding route definitions.

    private Route unknownRoute; // Represents the fallback route used when a navigation request targets an unknown or non-registered path.

    private Route home; // Represents the default or primary route of the application.

    /**
     * Indexes startup routes and identifies the unrestricted blank-path home route.
     *
     * @param allRoutes routes available at startup
     */
    @Autowired
    public RouteRegistry(List<Route> allRoutes) {
        for (Route route : allRoutes) {
            if (home == null && !route.requiresAuthentication() && route.getAllowedRoles().isEmpty() && route.getPath().isBlank())
                home = route;
            if (routes.containsKey(route.getPath()))
                throw new IllegalStateException("Duplicate route path: " + route.getPath());
            routes.put(route.getPath(), route);
        }
    }

    /**
     * Retrieves the home route of the application.
     * The home route typically serves as the default or main entry point
     * for the application, and may be defined during the initialization of
     * the route registry.
     *
     * @return the {@link Route} instance representing the home route.
     */
    public Route getHome() {
        return home;
    }

    /**
     * Registers a route by associating its path with the route definition.
     * Allows the router to resolve and handle navigation requests based on the path.
     *
     * @param route The route to be registered, containing the path and associated logic.
     *              The path must be unique within the router and determines the route's destination.
     */
    public void addRoute(Route route) {
        routes.put(route.getPath(), route);
    }

    /**
     * Navigates to a registered path, clearing the current UI and applying updates.
     *
     * @param path The requested route (e.g. "/")
     */
    public void handleRoute(String path, UI ui) {
        String normalizedPath = normalizeRoutePath(path);
        Route route = resolveRoute(normalizedPath);

        ui.clear();
        ui.setSeo(null, normalizedPath, route == null || route.requiresAuthentication() || !route.getAllowedRoles().isEmpty());

        if (route == null) {
            ui.add(new H1("404 Unknown Route: " + normalizedPath));
            return;
        }

        Collection<String> allowed = route.getAllowedRoles();
        SessionContext.access(context -> {
            context.attribute("routePath", normalizedPath);
            context.attribute("routeBasePath", route.getPath());

            User user = context.get("user", User.class, null);
            if (canAccess(route, user)) {
                route.load(ui);
                SeoPage page = route.getSeo(normalizedPath);
                if (page != null) ui.setSeo(page, normalizedPath, route.requiresAuthentication() || !route.getAllowedRoles().isEmpty());
            } else {
                ui.add(new H1("403 Forbidden Route: " + normalizedPath));
            }
        });
    }

    /**
     * Looks up an exact path, then its leading segment, and finally the configured fallback route.
     *
     * @param path requested path relative to the resource or route root
     * @return the resulting resolve route value
     */
    private Route resolveRoute(String path) {
        Route exact = this.routes.get(path);
        if (exact != null) {
            return exact;
        }

        int slashIndex = path.indexOf('/');
        if (slashIndex > 0) {
            Route dynamic = this.routes.get(path.substring(0, slashIndex));
            if (dynamic != null) {
                return dynamic;
            }
        }

        return unknownRoute;
    }

    /**
     * Resolves registered exact or leading-segment routes without a fallback.
     * @param path requested path
     * @return registered route or null
     */
    public Route find(String path) {
        String normalized = normalizeRoutePath(path);
        Route route = routes.get(normalized);
        if (route != null) return route;
        int slash = normalized.indexOf('/');
        return slash > 0 ? routes.get(normalized.substring(0, slash)) : null;
    }

    /**
     * Returns registered route definitions for sitemap generation.
     * @return immutable route collection
     */
    public Collection<Route> getRoutes() { return List.copyOf(routes.values()); }

    /**
     * Applies the same authentication and role rules to HTTP and WebSocket routes.
     * @param route requested route
     * @param user current user, or null
     * @return access decision
     */
    public boolean canAccess(Route route, User user) {
        if (user == null) return !route.requiresAuthentication() && route.getAllowedRoles().isEmpty();
        if (route.getAllowedRoles().isEmpty()) return true;
        for (String role : route.getAllowedRoles()) if (user.hasRole(role)) return true;
        return false;
    }

    /**
     * Removes surrounding whitespace and slash delimiters; null, blank, and root paths resolve to the home route path.
     *
     * @param path requested path relative to the resource or route root
     * @return the resulting normalize route path value
     */
    public String normalizeRoutePath(String path) {
        if (path == null || path.isBlank() || path.equals("/")) {
            return home == null ? "" : home.getPath();
        }

        String normalized = path.trim();
        int first = 0;
        int last = normalized.length();
        while (first < last && normalized.charAt(first) == '/') first++;
        while (last - first > 1 && normalized.charAt(last - 1) == '/') last--;
        return first == 0 && last == normalized.length() ? normalized : normalized.substring(first, last);
    }

    /**
     * Sends a navigation request to update the browser's URL to the provided path.
     * Encodes the path in UTF-8, prepares a binary payload, and transmits it
     * through the associated session's WebSocket channel.
     *
     * @param path The target route path to navigate to (e.g., "/home", "/about").
     *             Cannot be null; if null, the method returns without action.
     */
    protected void navigate(String path) {
        if (path == null)
            return;

        SessionContext.access(context -> {
            context.getUI()
                    .flushUpdates();
            context.send(TextPacketEncoder.encode(context.getChannel(), 0, path));
        });
    }

    /**
     * Sets the unknown route for the router. The unknown route is used as a fallback
     * mechanism when navigation is requested to a path that does not match any
     * registered routes. This route typically displays a "not found" or
     * similar page to the user.
     *
     * @param route the {@link Route} instance to use as the unknown route.
     */
    public void setUnknownRoute(Route route) {
        this.unknownRoute = route;
    }

}
