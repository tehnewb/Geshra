package com.example.demo.routes;

import com.example.demo.showcase.ShowcaseForms;
import com.example.demo.showcase.ShowcaseSamples;
import com.example.demo.showcase.ShowcaseStructure;
import com.example.demo.showcase.ShowcaseCollections;
import com.example.demo.showcase.ShowcaseMedia;
import com.example.demo.gallery.NodeLifecycleDemo;
import com.example.demo.gallery.OverlaysDemo;
import com.example.demo.gallery.VirtualListDemo;
import geshra.net.web.Route;
import geshra.net.web.SeoPage;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.H1;
import geshra.net.web.ui.components.Hyperlink;
import geshra.net.web.ui.components.Link;
import geshra.net.web.ui.components.Paragraph;
import org.springframework.stereotype.Component;

/**
 * Public, session-isolated catalog of Geshra's real component APIs at /showcase. All categories are
 * visible for source-to-browser coverage; no catalog state is retained by this singleton route.
 * Reloading resets every sample. The original /components gallery and benchmark fixtures remain intact.
 */
@Component
public class ShowcaseRoute implements Route {
    @Override
    public String getPath() { return "showcase"; }

    @Override
    public SeoPage getSeo() {
        return new SeoPage("Geshra UI showcase")
                .description("Interactive examples of every public Geshra UI component.");
    }

    @Override
    public void load(UI ui) {
        ui.setTitle("Geshra UI showcase");
        ui.attribute("class", "showcase");
        ui.add(new Link("/showcase-assets/showcase.css", "stylesheet"));
        ui.add(new H1("Geshra UI showcase"), new Paragraph("Every public component, rendered by Geshra. Edit controls and run actions to see Java callbacks. Reload this page to reset all examples."));
        ui.add(new Hyperlink("Reload and reset showcase", "/showcase"));
        ui.add(ShowcaseForms.create());
        ui.add(ShowcaseStructure.create());
        ui.add(ShowcaseCollections.create());
        ui.add(ShowcaseSamples.section("Disclosure, overlays and tooltips", ShowcaseSamples.sample("Details,Summary,Accordion,Dialog,Popover", "Native disclosure and overlay actions with Java state feedback.", OverlaysDemo.create()), ShowcaseStructure.tooltip()));
        ui.add(ShowcaseSamples.section("Virtualized data", ShowcaseSamples.sample("VirtualList", "A million lazy logical rows; fixed-height viewport and jump/refresh actions.", VirtualListDemo.create())));
        ui.add(ShowcaseMedia.create());
        ui.add(ShowcaseSamples.section("Component lifecycle", ShowcaseSamples.sample("Button,TextField", "Move, detach, restore, dispose and unsubscribe real retained components. Reload resets disposed examples.", NodeLifecycleDemo.create())));
        ui.add(new Paragraph("Coverage notes: hidden/resource/abstract/helper types are explained in the source inventory. Browser-native pickers vary by platform. TableSelectionMode currently stores policy only; it does not implement selectable rows. No Menu component exists."));
        ui.attribute("data-showcase-ready", "true");
    }
}
