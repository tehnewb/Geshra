package com.example.demo.routes;

import com.example.demo.gallery.FormControlsDemo;
import com.example.demo.gallery.GraphicsDemo;
import com.example.demo.gallery.NodeLifecycleDemo;
import com.example.demo.gallery.OverlaysDemo;
import com.example.demo.gallery.StatusLayoutsDemo;
import com.example.demo.gallery.VirtualListDemo;
import geshra.net.web.Route;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.H1;
import geshra.net.web.ui.components.Paragraph;
import org.springframework.stereotype.Component;

/**
 * Assembles the component gallery from focused, independent examples.
 * Spring discovers this public route at /components. Each section creates new
 * components for the current session rather than storing UI state in this singleton.
 */
@Component
public class ComponentsRoute implements Route {
    @Override
    public String getPath() {
        return "components";
    }

    @Override
    public void load(UI ui) {
        ui.setTitle("Component gallery");
        ui.attribute("style", "max-width:1000px;margin:24px auto;padding:16px;font:16px system-ui");
        ui.add(new H1("Component gallery"), new Paragraph("Native controls and Java callbacks. Each section demonstrates one part of Geshra."));

        // Read each factory independently, or reuse its section inside another route.
        ui.add(FormControlsDemo.create());
        ui.add(OverlaysDemo.create());
        ui.add(StatusLayoutsDemo.create());
        ui.add(NodeLifecycleDemo.create());
        ui.add(VirtualListDemo.create());
        ui.add(GraphicsDemo.create());
    }
}
