package geshra.net.web.ui;

import geshra.net.web.RouteRegistry;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.components.Option;
import geshra.net.web.ui.components.OptionGroup;
import geshra.net.web.ui.components.Select;
import geshra.net.web.ui.components.Paragraph;
import geshra.net.web.ui.components.Div;
import geshra.net.web.ui.components.tab.Tabbed;
import geshra.net.web.ui.components.tab.TabButton;
import geshra.net.web.ui.components.tree.Tree;
import geshra.net.web.ui.components.tree.TreeNode;
import geshra.net.web.ui.components.tree.TreeSelectionMode;
import geshra.net.web.ui.event.DomEvent;
import geshra.net.web.ui.event.KeyDownEvent;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Wire-level ordering, keyboard navigation and selection-policy regressions. Real component event
 * dispatch is exercised without a browser; independent Chrome/Firefox checks cover native option
 * selection, trusted key defaults, focus styling and accessibility attributes in the live DOM.
 */
class CompositeUiRegressionTest {
    /**
     * Assembly in an unattached container still gives the initial root a unique native Tab stop.
     */
    @Test
    void initializesTreeTabStopBeforeContainerAttachment() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "tree-initial-focus", channel);
        SessionContext.set(context);
        try {
            TreeNode root = new TreeNode("Root");
            TreeNode child = new TreeNode("Child");
            root.addNode(child);
            Tree tree = new Tree(root);
            Div container = new Div(tree);
            context.getUI()
                    .add(container);
            List<ObservedMutation> mutations = drain(channel);
            assertAttribute(mutations, root, "tabindex", "0");
            assertAttribute(mutations, root, "aria-selected", "false");
            assertAttribute(mutations, child, "aria-selected", "false");
        } finally {
            SessionContext.set(null);
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Disposed and retained-detached nodes are excluded from selection and traversal; restoration
     * preserves logical ordering, and whole-tree disposal emits no focus command to dead nodes.
     */
    @Test
    void maintainsTreeOwnershipThroughRemovalAndRetainedReattachment() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "tree-lifecycle", channel);
        SessionContext.set(context);
        try {
            TreeNode root = new TreeNode("Project");
            TreeNode branch = new TreeNode("Sources");
            TreeNode leaf = new TreeNode("Main.java");
            TreeNode sibling = new TreeNode("README.md");
            branch.addNode(leaf);
            TreeNode helper = new TreeNode("Helper.java");
            branch.addNode(helper);
            root.addNode(branch);
            root.addNode(sibling);
            Tree tree = new Tree(root);
            context.getUI()
                    .add(tree);
            root.setExpanded(true);
            branch.setExpanded(true);
            key(leaf, Key.SPACE);
            leaf.publish(new DomEvent(leaf, "focus", ""));
            drain(channel);
            leaf.detach();
            assertThat(tree.getSelection())
                    .isEmpty();
            key(branch, Key.ARROW_DOWN);
            assertFocus(drain(channel), helper);
            branch.addNode(leaf);
            assertThat(drain(channel).stream().anyMatch(mutation -> mutation.type() == DOMUpdateType.MOVE_CHILD.getCode() && Integer.toString(helper.getComponentID()).equals(mutation.parameters().get(DOMUpdateParam.INSERT_ID.getCode()))))
                    .isTrue();
            assertThat(leaf.isAttached())
                    .isTrue();
            key(branch, Key.ARROW_DOWN);
            assertFocus(drain(channel), leaf);
            key(leaf, Key.SPACE);
            leaf.publish(new DomEvent(leaf, "focus", ""));
            leaf.dispose();
            assertThat(tree.getSelection())
                    .isEmpty();
            key(root, Key.END);
            assertFocus(drain(channel), sibling);
            key(branch, Key.ENTER);
            branch.publish(new DomEvent(branch, "focus", ""));
            branch.dispose();
            assertThat(tree.getSelection())
                    .isEmpty();
            key(root, Key.ARROW_RIGHT);
            assertFocus(drain(channel), sibling);
            tree.dispose();
            assertThat(drain(channel).stream().anyMatch(mutation -> mutation.type() == DOMUpdateType.CALL_METHOD.getCode() && "focus".equals(mutation.parameters().get(DOMUpdateParam.KEY.getCode()))))
                    .isFalse();
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Already-owned roots cannot be shared between coordinators or inserted into another hierarchy.
     */
    @Test
    void rejectsCrossTreeOwnershipWithoutMutatingTheOriginalHierarchy() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "tree-ownership", channel);
        SessionContext.set(context);
        try {
            TreeNode root = new TreeNode("Owned root");
            Tree original = new Tree(root);
            context.getUI()
                    .add(original);
            TreeNode otherRoot = new TreeNode("Other root");
            assertThatThrownBy(() -> new Tree(root))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> otherRoot.addNode(root))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(original.getRoot())
                    .isSameAs(root);
            assertThat(root.getTree())
                    .isSameAs(original);
            assertThat(root.isAttached())
                    .isTrue();
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Disabling/re-enabling selectors updates browser tab stops even when no tab is being selected.
     */
    @Test
    void restoresTabStopsAcrossDisabledStates() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "tab-enablement", channel);
        SessionContext.set(context);
        try {
            Tabbed tabs = new Tabbed();
            tabs.addTab("First", new Paragraph("First content"));
            tabs.addTab("Second", new Paragraph("Second content"));
            tabs.selectTab(0);
            TabButton first = tabs.getSelectedTab();
            tabs.selectTab(1);
            TabButton second = tabs.getSelectedTab();
            tabs.selectTab(0);
            context.getUI()
                    .add(tabs);
            drain(channel);
            first.setEnabled(false);
            assertAttribute(drain(channel), second, "tabindex", "0");
            second.setEnabled(false);
            assertAttribute(drain(channel), second, "tabindex", "-1");
            first.setEnabled(true);
            assertAttribute(drain(channel), first, "tabindex", "0");
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * The untouched default instruction follows all nested option creation and property assignment.
     */
    @Test
    void ordersSelectDefaultAfterItsEntireOptionSubtree() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "select-default-order", channel);
        SessionContext.set(context);
        try {
            UI ui = context.getUI();
            ui.beginUpdate();
            Select select = new Select();
            select.add(new OptionGroup("Languages", new Option("java", "Java")), new Option("", "Empty"));
            ui.add(select);
            ui.endUpdate();
            List<ObservedMutation> mutations = drain(channel);
            int initialization = -1;
            for (int index = 0; index < mutations.size(); index++) {
                ObservedMutation mutation = mutations.get(index);
                if (mutation.type() == DOMUpdateType.INITIAL_SELECT.getCode()) initialization = index;
            }
            assertThat(initialization)
                    .isGreaterThan(0);
            assertThat(mutations.get(initialization).target())
                    .isEqualTo(select.getComponentID());
            for (int index = initialization + 1; index < mutations.size(); index++) {
                assertThat(mutations.get(index).type())
                        .isNotIn((int) DOMUpdateType.APPEND_CHILD.getCode(), (int) DOMUpdateType.SET_TYPED_PROPERTY.getCode());
            }
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Visible traversal and keyboard activation preserve distinct focus and selection state.
     */
    @Test
    void navigatesTreeAndEnforcesSelectionCardinality() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "tree-keys", channel);
        SessionContext.set(context);
        try {
            TreeNode root = new TreeNode("Project");
            TreeNode branch = new TreeNode("Sources");
            TreeNode leaf = new TreeNode("Main.java");
            TreeNode sibling = new TreeNode("README.md");
            branch.addNode(leaf);
            root.addNode(branch);
            root.addNode(sibling);
            Tree tree = new Tree(root);
            context.getUI()
                    .add(tree);
            drain(channel);
            key(root, Key.ARROW_RIGHT);
            assertThat(root.isExpanded())
                    .isTrue();
            key(root, Key.ARROW_RIGHT);
            assertFocus(drain(channel), branch);
            assertThat(tree.getSelection())
                    .isEmpty();
            key(branch, Key.ARROW_RIGHT);
            key(branch, Key.ARROW_RIGHT);
            assertFocus(drain(channel), leaf);
            key(leaf, Key.END);
            assertFocus(drain(channel), sibling);
            key(branch, Key.SPACE);
            key(sibling, Key.SPACE);
            assertThat(tree.getSelection())
                    .containsExactly(branch, sibling);
            tree.setSelectionMode(TreeSelectionMode.SINGLE);
            assertThat(tree.getSelection())
                    .containsExactly(branch);
            key(leaf, Key.ENTER);
            assertThat(tree.getSelection())
                    .containsExactly(leaf);
            tree.setSelectionMode(TreeSelectionMode.NONE);
            key(sibling, Key.SPACE);
            assertThat(tree.getSelection())
                    .isEmpty();
            key(leaf, Key.HOME);
            assertFocus(drain(channel), root);
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Collapse moves actual descendant focus to its branch but never steals focus after a blur.
     */
    @Test
    void handsOffCollapsedFocusWithoutStealingExternalFocus() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "tree-collapse-focus", channel);
        SessionContext.set(context);
        try {
            TreeNode root = new TreeNode("Project");
            TreeNode leaf = new TreeNode("README.md");
            root.addNode(leaf);
            context.getUI()
                    .add(new Tree(root));
            root.setExpanded(true);
            leaf.publish(new DomEvent(leaf, "focus", ""));
            drain(channel);
            root.setExpanded(false);
            assertFocus(drain(channel), root);
            root.setExpanded(true);
            leaf.publish(new DomEvent(leaf, "focus", ""));
            leaf.publish(new DomEvent(leaf, "blur", ""));
            drain(channel);
            root.setExpanded(false);
            assertThat(drain(channel).stream().anyMatch(mutation -> mutation.type() == DOMUpdateType.CALL_METHOD.getCode() && "focus".equals(mutation.parameters().get(DOMUpdateParam.KEY.getCode()))))
                    .isFalse();
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Tab navigation activates the linked panel and emits native focus to the new selector.
     */
    @Test
    void activatesTabsThroughArrowNavigation() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "tabs-keys", channel);
        SessionContext.set(context);
        try {
            Tabbed tabs = new Tabbed();
            tabs.addTab("First", new Paragraph("First content"));
            tabs.addTab("Second", new Paragraph("Second content"));
            tabs.selectTab(0);
            context.getUI()
                    .add(tabs);
            drain(channel);
            key(tabs.getSelectedTab(), Key.ARROW_RIGHT);
            assertThat(tabs.getSelectedTab().getText())
                    .isEqualTo("Second");
            assertFocus(drain(channel), tabs.getSelectedTab());
            key(tabs.getSelectedTab(), Key.HOME);
            assertThat(tabs.getSelectedTab().getText())
                    .isEqualTo("First");
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Pending metadata is delivered before a later title control packet inside one update group.
     */
    @Test
    void ordersTitleAfterEarlierSeoReset() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "title-order", channel);
        SessionContext.set(context);
        try {
            UI ui = context.getUI();
            ui.beginUpdate();
            ui.setSeo(null, "/title");
            ui.setTitle("Route title");
            ui.endUpdate();
            BinaryWebSocketFrame metadata = channel.readOutbound();
            BinaryWebSocketFrame title = channel.readOutbound();
            try {
                assertThat((int) metadata.content().getUnsignedByte(0))
                        .isEqualTo(1);
                assertThat((int) title.content().getUnsignedByte(0))
                        .isEqualTo(3);
                assertThat(title.content().toString(StandardCharsets.UTF_8))
                        .contains("Route title");
            } finally {
                metadata.release();
                title.release();
            }
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Dispatches a normal unmodified key through the public component event pipeline.
     * @param component event target
     * @param key logical browser key
     */
    private static void key(Component component, Key key) {
        /*
         * Real event registration, rather than directly invoking implementation methods, is exercised.
         */
        component.publish(new KeyDownEvent(key, false, 0));
    }

    /**
     * Verifies that the final native focus request names the expected live component.
     * @param mutations decoded browser commands
     * @param target expected focus recipient
     */
    private static void assertFocus(List<ObservedMutation> mutations, Component target) {
        /*
         * Observe the actual browser command rather than a private focus-model field.
         */
        List<ObservedMutation> focus = mutations.stream()
                .filter(mutation -> mutation.type() == DOMUpdateType.CALL_METHOD.getCode() && "focus".equals(mutation.parameters().get(DOMUpdateParam.KEY.getCode())))
                .toList();
        assertThat(focus)
                .isNotEmpty();
        assertThat(focus.getLast().target())
                .isEqualTo(target.getComponentID());
    }

    /**
     * Checks the final emitted attribute for a particular native component.
     * @param mutations decoded browser commands
     * @param target expected attribute owner
     * @param key native attribute name
     * @param value expected final serialized value
     */
    private static void assertAttribute(List<ObservedMutation> mutations, Component target, String key, String value) {
        /*
         * A later write supersedes earlier writes exactly as it does in the browser DOM.
         */
        String actual = null;
        for (ObservedMutation mutation : mutations) {
            if (mutation.type() == DOMUpdateType.SET_ATTRIBUTE.getCode() && mutation.target() == target.getComponentID() && key.equals(mutation.parameters().get(DOMUpdateParam.KEY.getCode()))) actual = mutation.parameters()
                    .get(DOMUpdateParam.VALUE.getCode());
        }
        assertThat(actual)
                .isEqualTo(value);
    }

    /**
     * Copies emitted legacy DOM mutations and releases all reference-counted frames.
     * @param channel owned embedded transport
     * @return mutations in browser application order
     */
    private static List<ObservedMutation> drain(EmbeddedChannel channel) {
        /*
         * Full parameter decoding preserves command/target relationships across multiple packets.
         */
        List<ObservedMutation> mutations = new ArrayList<>();
        BinaryWebSocketFrame frame;
        while ((frame = channel.readOutbound()) != null) {
            try {
                ByteBuf bytes = frame.content();
                assertThat((int) bytes.readUnsignedByte())
                        .isEqualTo(1);
                int count = bytes.readUnsignedShort();
                for (int index = 0; index < count; index++) {
                    int type = bytes.readUnsignedByte();
                    int target = bytes.readInt();
                    int parameters = bytes.readUnsignedByte();
                    Map<Integer, String> values = new HashMap<>();
                    for (int parameter = 0; parameter < parameters; parameter++) {
                        int key = bytes.readUnsignedByte();
                        int length = bytes.readUnsignedShort();
                        values.put(key, bytes.readCharSequence(length, StandardCharsets.UTF_8).toString());
                    }
                    mutations.add(new ObservedMutation(type, target, values));
                }
            } finally {
                frame.release();
            }
        }
        return mutations;
    }
}
