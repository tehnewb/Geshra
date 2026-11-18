package benchmark;

import geshra.net.web.Route;
import geshra.net.web.SeoPage;
import geshra.net.web.ui.UI;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Creates one server-owned counter, form echo, and unvirtualized 1,000-row table per session.
 * Every measured action executes Java on the server. Native tags, row data, operation order,
 * and completion revisions match the other isolated fixtures. Route beans hold no UI state.
 */
@Component
public final class MatchedRoute implements Route {
    @Override
    public SeoPage getSeo() { return new SeoPage("Matched benchmark"); }

    @Override
    public String getPath() { return ""; }

    @Override
    public Collection<String> getAllowedRoles() { return List.of(); }

    @Override
    public void load(UI ui) { ui.add(new MatchedState().root()); }
}
