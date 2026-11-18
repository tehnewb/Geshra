package com.example.demo.showcase;

import geshra.net.web.ui.components.*;
import geshra.net.web.ui.components.tab.Tabbed;
import geshra.net.web.ui.components.tab.TabButton;
import geshra.net.web.ui.components.table.*;
import geshra.net.web.ui.components.tree.*;

/**
 * Interactive tables, tabs and trees built from their public Geshra APIs. Mutable data and callbacks
 * belong to the current UI session. Unsupported selection behavior is disclosed rather than simulated
 * as an implementation of TableSelectionMode.
 */
public final class ShowcaseCollections {
    /**
     * Prevents construction of this stateless category factory.
     */
    private ShowcaseCollections() { }

    /**
     * Creates collection samples, including native subcomponents in their valid parent structures.
     * @return new collection category
     */
    public static Section create() {
        /*
         * Keep mutation controls outside the component being cleared so reset remains reachable.
         */
        Table<String> table = new Table<>();
        table.columns("Name", "Language");
        table.row("Ada", "Java");
        table.row("Lin", "JavaScript");
        table.setSelectionMode(TableSelectionMode.SINGLE);
        table.add(new Caption("Example people"));
        Paragraph tableState = new Paragraph("2 rows; selection policy SINGLE (row selection is not implemented)");
        Button clear = ShowcaseSamples.action("Clear table", () -> { table.clearRows(); tableState.setText("Empty table"); });
        Button reset = ShowcaseSamples.action("Reset table", () -> { table.clearRows(); table.row("Ada", "Java"); table.row("Lin", "JavaScript"); tableState.setText("2 rows restored"); });
        Tabbed tabs = new Tabbed();
        tabs.addTab("Overview", new Paragraph("Overview tab content"));
        tabs.addTab("Settings", new Paragraph("Settings tab content"));
        tabs.selectTab(0);
        TabButton overview = tabs.getSelectedTab();
        tabs.selectTab(1);
        TabButton settings = tabs.getSelectedTab();
        tabs.selectTab(0);
        Button disableSelected = ShowcaseSamples.action("Disable selected tab", () -> tabs.getSelectedTab().setEnabled(false));
        Button disableAll = ShowcaseSamples.action("Disable all tabs", () -> { overview.setEnabled(false); settings.setEnabled(false); });
        Button enableAll = ShowcaseSamples.action("Enable all tabs", () -> { overview.setEnabled(true); settings.setEnabled(true); });
        TreeNode root = new TreeNode("Project");
        TreeNode branch = new TreeNode("Sources");
        TreeNode leaf = new TreeNode("Main.java");
        branch.addNode(leaf);
        root.addNode(branch);
        root.addNode(new TreeNode("README.md"));
        Tree tree = new Tree(root);
        tree.ariaLabel("Project files");
        Paragraph treeState = new Paragraph("No selection read yet");
        Button read = ShowcaseSamples.action("Read tree selection", () -> treeState.setText("Selected nodes: " + tree.getSelection().size()));
        Button rename = ShowcaseSamples.action("Rename tree branch", () -> branch.setValue("Renamed sources"));
        Select mode = new Select();
        mode.addOption("SINGLE", "Single selection");
        mode.addOption("MULTIPLE", "Multiple selection");
        mode.addOption("NONE", "No selection");
        mode.setValue("MULTIPLE");
        mode.ariaLabel("Tree selection mode");
        mode.addValueChangeListener(event -> tree.setSelectionMode(TreeSelectionMode.valueOf(mode.getValue())));
        Button detachLeaf = new Button("Detach Main.java");
        Button restoreLeaf = new Button("Restore Main.java");
        restoreLeaf.setEnabled(false);
        Button disposeLeaf = new Button("Dispose Main.java");
        Button disposeBranch = new Button("Dispose Sources branch");
        Button disposeTree = new Button("Dispose tree");
        detachLeaf.addClickListener(event -> { leaf.detach(); detachLeaf.setEnabled(false); restoreLeaf.setEnabled(true); });
        restoreLeaf.addClickListener(event -> { branch.addNode(leaf); detachLeaf.setEnabled(true); restoreLeaf.setEnabled(false); });
        disposeLeaf.addClickListener(event -> { leaf.dispose(); detachLeaf.setEnabled(false); restoreLeaf.setEnabled(false); disposeLeaf.setEnabled(false); });
        disposeBranch.addClickListener(event -> { branch.dispose(); detachLeaf.setEnabled(false); restoreLeaf.setEnabled(false); disposeLeaf.setEnabled(false); disposeBranch.setEnabled(false); rename.setEnabled(false); });
        disposeTree.addClickListener(event -> { tree.dispose(); mode.setEnabled(false); detachLeaf.setEnabled(false); restoreLeaf.setEnabled(false); disposeLeaf.setEnabled(false); disposeBranch.setEnabled(false); disposeTree.setEnabled(false); rename.setEnabled(false); });
        return ShowcaseSamples.section("Tables, tabs and trees",
                ShowcaseSamples.sample("Table,TableHead,TableBody,TableRow,TableColumn,TableCell,Caption", "Real table headers/cells. Clear demonstrates empty state; reset restores rows. Table selection currently has no interactive implementation.", table, new ButtonBar(clear, reset), tableState),
                ShowcaseSamples.sample("Tabbed,TabButton", "Use native Tab to enter the enabled tab stop; Left/Right/Home/End activate tabs. Disabled tabs are skipped and enabling restores a tab stop.", tabs, new ButtonBar(disableSelected, disableAll, enableAll)),
                ShowcaseSamples.sample("Tree,TreeNode", "Tab into Project. Use arrows/Home/End to move focus, Right/Left to expand/collapse, Space to toggle multiple selection and Enter to select. Focus and selection are independent. Detach/restore preserves identity; disposal is permanent until reload.", mode, tree, new ButtonBar(read, rename), new ButtonBar(detachLeaf, restoreLeaf, disposeLeaf, disposeBranch, disposeTree), treeState));
    }
}
