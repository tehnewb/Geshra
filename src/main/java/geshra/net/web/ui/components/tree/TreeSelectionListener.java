package geshra.net.web.ui.components.tree;

import geshra.event.EventHandler;
import geshra.net.web.ui.event.ClickEvent;

/**
 * Adapts pointer selection to the tree's shared selection and roving-focus policy. The node and
 * callback are session-owned; keyboard activation uses the same coordinator without fabricated clicks.
 */
record TreeSelectionListener(TreeNode node) implements EventHandler<ClickEvent> {
    @Override
    public void handle(ClickEvent event) {
        Tree tree = node.getTree();
        if (tree == null) return;
        tree.focusNode(node, true);
        tree.selectNode(node, event.isCtrlPressed() || event.isMetaPressed(), event.isShiftPressed());
    }
}
