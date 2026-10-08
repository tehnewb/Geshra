package com.example.demo;

import geshra.net.web.Cookie;
import geshra.net.web.Route;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.Button;
import geshra.net.web.ui.components.H1;
import geshra.net.web.ui.components.Paragraph;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Demonstrates a protected route, typed session values, server-readable cookies, and logout.
 */
@Component
public class AccountRoute implements Route {
    @Override
    public String getPath() { return "account"; }

    @Override
    public Collection<String> getAllowedRoles() { return List.of("member"); }

    @Override
    public void load(UI ui) {
        SessionContext session = SessionContext.get();
        int visits = session.get("visits", Integer.class, 0) + 1;
        session.attribute("visits", visits);
        Paragraph preference = new Paragraph("Preference: " + session.cookie("privatePreference"));
        Button save = new Button("Save private preference");
        save.addClickListener(event -> session.setCookie(new Cookie("privatePreference", "dark").httpOnly(true)));
        Button read = new Button("Read preference");
        read.addClickListener(event -> preference.setText("Preference: " + session.cookie("privatePreference")));
        Button logout = new Button("Sign out");
        logout.addClickListener(event -> session.logout());
        ui.add(new H1("Welcome " + session.getUser().getUsername()), new Paragraph("Session visits: " + visits), preference, save, read, logout);
    }
}
