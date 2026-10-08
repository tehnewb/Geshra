package geshra.net.web;

import geshra.net.web.ui.UI;
import java.util.Collection;
import java.util.List;

/**
 * Represents an application route that defines a specific path and its associated behavior.
 * Routes are used to handle navigation and attach content to the provided {@link UI}.
 *
 * @author Albert Beaupre
 */
public interface Route {

    /**
     * Loads the given UI by applying the operations defined in the current route.
     *
     * @param ui the {@link UI} instance to load. Represents the root container for
     *           all UI components within the session. The method's implementation
     *           determines how the UI is updated or set up based on the route.
     */
    void load(UI ui);

    /**
     * Retrieves the path associated with this route.
     * The path is a defined string
     */
    String getPath();

    /**
     * Retrieves a collection of roles that are allowed to access the route.
     *
     * @return a collection of role names as strings, representing the roles permitted
     * to access the specific route
     */
    default Collection<String> getAllowedRoles() { return List.of(); }

    /**
     * Requires login without requiring a specific role.
     * @return whether an authenticated user is required
     */
    default boolean requiresAuthentication() { return false; }

    /**
     * Supplies metadata and initial public content for this page.
     * @return page metadata, or null to use the application's HTML shell
     */
    default SeoPage getSeo() { return null; }

    /**
     * Supplies request-path-specific metadata for dynamic routes.
     * @param path normalized requested path
     * @return metadata for that path
     */
    default SeoPage getSeo(String path) { return getSeo(); }

    /**
     * Lists concrete public route paths for the generated sitemap.
     * @return route paths; dynamic routes may override this with actual item URLs
     */
    default Collection<String> getSitemapPaths() { return List.of(getPath()); }

}
