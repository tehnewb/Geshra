package geshra.net.web;

import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.Paragraph;
import java.util.Collection;

/**
 * Route fixture for initial HTML and access-control integration checks.
 * @param path registered path
 * @param roles required roles
 * @param page page metadata
 */
record FeatureTestRoute(String path, Collection<String> roles, SeoPage page) implements Route {
    @Override
    public String getPath() { return path; }

    @Override
    public Collection<String> getAllowedRoles() { return roles; }

    @Override
    public SeoPage getSeo() { return page; }

    @Override
    public void load(UI ui) { ui.add(new Paragraph("Interactive route")); }
}
