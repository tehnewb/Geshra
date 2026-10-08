package com.example.demo.routes;

import geshra.net.web.Route;
import geshra.net.web.SeoPage;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.Button;
import geshra.net.web.ui.components.H1;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Start here for the smallest complete Geshra route: create a heading, attach a Java
 * click listener, and add the components to the current UI. The empty route path
 * represents the home page. Public SEO content also works before JavaScript loads.
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

        // Route beans are shared by Spring; create mutable UI components inside load.
        H1 heading = new H1("Hello from Spring Boot");
        Button button = new Button("Click me");

        // A browser click invokes this Java callback and sends the changed heading back.
        button.addClickListener(event -> heading.setText("The Java listener handled your click"));
        ui.add(heading);
        ui.add(button);
    }
}
