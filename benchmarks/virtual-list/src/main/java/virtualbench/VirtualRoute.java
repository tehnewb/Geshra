package virtualbench;

import geshra.net.web.Route;
import geshra.net.web.SeoPage;
import geshra.net.web.ui.UI;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Creates session-local lazy rows, with dataset size selected by an isolated server property.
 */
@Component
public final class VirtualRoute implements Route {
    @Override
    public SeoPage getSeo() { return new SeoPage("Virtual benchmark"); }

    @Override
    public String getPath() { return ""; }

    @Override
    public Collection<String> getAllowedRoles() { return List.of(); }

    @Override
    public void load(UI ui) { ui.add(new VirtualState(Integer.getInteger("virtual.count", 100000)).root()); }
}
