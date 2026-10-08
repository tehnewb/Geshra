package com.example.demo;

import geshra.net.web.Route;
import geshra.net.web.SeoPage;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.Button;
import geshra.net.web.ui.components.H1;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Demonstrates a consumer-owned home route that builds server-driven UI components when the browser navigates to the root path.
 */
@Component
public class HomeRoute implements Route {
    private final SeoPage seo = new SeoPage("Hello from Spring Boot")
            .description("An example of Java UI components in a Spring application.")
            .contentHtml("<main><h1>Hello from Spring Boot</h1><p>Java UI components in a Spring application.</p><a href=\"/components\">Component gallery</a><a href=\"/login\">Sign in</a></main>"); // Public initial content for crawlers and clients without JavaScript.

    @Override
    public SeoPage getSeo() { return seo; }
    @Override
    public String getPath() {
        return "";
    }
    @Override
    public Collection<String> getAllowedRoles() {
        return List.of();
    }

    @Override
    public void load(UI ui) {
        ui.setTitle("Spring Boot example");
        H1 heading = new H1("Hello from Spring Boot");
        Button button = new Button("Click me");
        button.addClickListener(event -> heading.setText("The Java listener handled your click"));
        ui.add(heading);
        ui.add(button);
    }
}
