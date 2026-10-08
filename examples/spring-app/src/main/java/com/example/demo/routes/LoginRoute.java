package com.example.demo.routes;

import geshra.net.web.Route;
import geshra.net.web.SeoPage;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.*;
import org.springframework.stereotype.Component;

/**
 * Public sign-in page using native form validation and a server-side session callback.
 * Run the example with demo.password set to enable the demo account. Authentication
 * verifies the supplied credentials and rotates the session cookie before navigation.
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
        // Native required fields and autocomplete retain normal browser form behavior.
        TextField username = new TextField("Username", "");
        username.setProperty("required", true);
        username.attribute("autocomplete", "username");
        PasswordField password = new PasswordField();
        password.setRequired(true);
        password.attribute("autocomplete", "current-password");
        Paragraph status = new Paragraph("");
        status.attribute("role", "status");

        // Submitting the form handles Enter as well as clicking its submit button.
        Form form = new Form();
        form.add(new Label("Username", username), username, new Label("Password", password), password, new Button("Sign in"), status);
        form.addSubmitListener(event -> {
            SessionContext session = SessionContext.get();

            // Geshra checks credentials asynchronously; change the page in the result callback.
            session.login(username.getValue(), password.getValue(), result -> {
                if (result.isSuccess()) session.navigate("/account");
                else status.setText("Username or password was not accepted");
            });
        });
        ui.add(new H1("Sign in"), form);
    }
}
