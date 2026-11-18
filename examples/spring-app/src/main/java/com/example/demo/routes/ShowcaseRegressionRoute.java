package com.example.demo.routes;

import com.example.demo.showcase.SelectRegressions;
import com.example.demo.showcase.SplitValueRegressions;
import com.example.demo.showcase.ShowcaseCollections;
import geshra.net.web.Route;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.H1;
import geshra.net.web.ui.components.Link;
import geshra.net.web.ui.components.Div;
import geshra.net.web.ui.components.Button;
import geshra.net.web.ui.components.ButtonBar;
import geshra.net.web.ui.components.Paragraph;
import geshra.net.web.ui.components.tree.Tree;
import geshra.net.web.ui.components.tree.TreeNode;
import org.springframework.stereotype.Component;

/**
 * Dedicated real-API fixtures for UI bug verification without changing the 113-type main catalog.
 * This route intentionally has no SeoPage, exercising UI.setTitle ordering after the router's SEO
 * reset. All controls and callback state are newly created for the current session.
 */
@Component
public class ShowcaseRegressionRoute implements Route {
    @Override
    public String getPath() { return "showcase-regressions"; }

    @Override
    public void load(UI ui) {
        ui.setTitle("Geshra UI regression fixtures");
        ui.attribute("class", "showcase");
        ui.add(new Link("/showcase-assets/showcase.css", "stylesheet"));
        ui.add(new H1("UI regression fixtures"), new Paragraph("Untouched defaults, explicit selection and keyboard controls. Values are read back from Java; reload resets each fixture."));
        ui.add(SelectRegressions.create(), SplitValueRegressions.create(), ShowcaseCollections.create());
        Tree empty = new Tree();
        empty.ariaLabel("Empty tree");
        ui.add(new Paragraph("Empty tree remains a labeled keyboard stop"), empty);
        TreeNode longRoot = new TreeNode("Long root");
        for (int index = 0; index < 100; index++) longRoot.addNode(new TreeNode("Row " + index));
        Tree longTree = new Tree(longRoot);
        longTree.ariaLabel("Long tree");
        longRoot.setExpanded(true);
        Div scroll = new Div(longTree);
        scroll.attribute("data-regression", "long-tree");
        scroll.attribute("style", "height:140px;overflow:auto;border:1px solid GrayText;margin:16px 0");
        ui.add(scroll);
        TreeNode orderRoot = new TreeNode("Sibling root");
        TreeNode first = new TreeNode("A");
        orderRoot.addNode(first);
        orderRoot.addNode(new TreeNode("B"));
        orderRoot.addNode(new TreeNode("C"));
        Tree order = new Tree(orderRoot);
        order.ariaLabel("Sibling restore tree");
        orderRoot.setExpanded(true);
        Button detach = new Button("Detach A");
        Button restore = new Button("Restore A");
        restore.setEnabled(false);
        detach.addClickListener(event -> { first.detach(); detach.setEnabled(false); restore.setEnabled(true); });
        restore.addClickListener(event -> { orderRoot.addNode(first); detach.setEnabled(true); restore.setEnabled(false); });
        Div restoreOrder = new Div(order, new ButtonBar(detach, restore));
        restoreOrder.attribute("data-regression", "restore-order");
        ui.add(restoreOrder);
        ui.attribute("data-showcase-regressions-ready", "true");
    }
}
