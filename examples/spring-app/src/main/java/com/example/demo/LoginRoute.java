package com.example.demo;

import geshra.net.web.Route;
import geshra.net.web.SeoPage;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.*;
import org.springframework.stereotype.Component;

/**
 * Demonstrates login using the native form and one server-side session callback.
 */
@Component
public class LoginRoute implements Route {
    private final SeoPage seo = new SeoPage("Sign in")
            .noIndex(); // Shared non-indexable login metadata.

    @Override
    public String getPath() { return "login"; }

    @Override
    public SeoPage getSeo() { return seo; }

    @Override
    public void load(UI ui) {
        TextField username = new TextField("Username", "");
        username.setProperty("required", true);
        username.attribute("autocomplete", "username");
        PasswordField password = new PasswordField();
        password.setRequired(true);
        password.attribute("autocomplete", "current-password");
        Paragraph status = new Paragraph("");
        status.attribute("role", "status");
        Form form = new Form();
        form.add(new Label("Username", username), username, new Label("Password", password), password, new Button("Sign in"), status);
        form.addSubmitListener(event -> {
            SessionContext session = SessionContext.get();
            session.login(username.getValue(), password.getValue(), result -> {
                if (result.isSuccess()) session.navigate("/account");
                else status.setText("Username or password was not accepted");
            });
        });
        ui.add(new H1("Sign in"), form);
    }
}
