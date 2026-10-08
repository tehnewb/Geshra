package geshra.net.web.ui.components.tree;

import geshra.event.EventHandler;
import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;
import geshra.net.web.ui.components.Div;
import geshra.net.web.ui.components.Span;
import geshra.net.web.ui.css.Display;
import geshra.net.web.ui.css.FontWeight;
import geshra.net.web.ui.css.UserSelect;
import geshra.net.web.ui.event.ClickEvent;
import java.util.*;

/**
 * Applies single, toggle, and inclusive range selection to one TreeNode. Runs on the owning session event loop. Ordinary clicks reuse the tree selection deque; shift clicks allocate a preorder list to locate the inclusive range.
 */
record TreeSelectionListener(TreeNode node) implements EventHandler<ClickEvent> {
    @Override
    public void handle(ClickEvent event) {
        Tree tree = node.getTree();
        ArrayDeque<TreeNode> selection = tree.getSelection();
        boolean ctrl = event.isCtrlPressed();
        boolean shift = event.isShiftPressed();
        TreeSelectionMode mode = tree.getSelectionMode();

        if (mode == TreeSelectionMode.NONE) return;

        // SINGLE mode is unchanged…
        if (mode == TreeSelectionMode.SINGLE) {
            clear(selection);
            selectNode(node);
            selection.add(node);
            return;
        }

        // MULTIPLE mode
        if (ctrl) {
            toggleNode(node, selection);
        } else if (shift && !selection.isEmpty()) {
            TreeNode last = selection.peekLast();
            clear(selection);
            List<TreeNode> range = findRange(last, node);
            for (TreeNode n : range) {
                selectNode(n);
                selection.add(n);
            }
        } else {
            clear(selection);
            selectNode(node);
            selection.add(node);
        }
    }

    /**
     * Removes selection highlighting from every selected node before emptying the selection deque.
     *
     * @param sel mutable selection deque
     */
    private void clear(ArrayDeque<TreeNode> sel) {
        for (TreeNode n : sel) unselectNode(n);
        sel.clear();
    }

    /**
     * Adds an unselected node or removes an already selected node, keeping its highlight consistent.
     *
     * @param n tree node whose selection state changes
     * @param sel mutable selection deque
     */
    private void toggleNode(TreeNode n, ArrayDeque<TreeNode> sel) {
        if (n.isSelected()) {
            unselectNode(n);
            sel.remove(n);
        } else {
            selectNode(n);
            sel.add(n);
        }
    }

    /**
     * Marks the node selected and queues its highlight style.
     *
     * @param n tree node whose selection state changes
     */
    private void selectNode(TreeNode n) {
        n.setSelected(true);
    }

    /**
     * Marks the node unselected and queues removal of its highlight.
     *
     * @param n tree node whose selection state changes
     */
    private void unselectNode(TreeNode n) {
        n.setSelected(false);
    }

    /**
     * Pre-order–flatten the tree, find the indices of start & end, and
     * return the inclusive sub-list between them.
     */
    private List<TreeNode> findRange(TreeNode start, TreeNode end) {
        List<TreeNode> flat = new ArrayList<>();
        buildFlatList(node.getTree().getRoot(), flat);
        int i1 = flat.indexOf(start);
        int i2 = flat.indexOf(end);
        if (i1 < 0 || i2 < 0) return Collections.emptyList();
        if (i1 > i2) {
            int t = i1;
            i1 = i2;
            i2 = t;
        }
        return flat.subList(i1, i2 + 1);
    }

    /**
     * Appends nodes in preorder to the destination list, retaining display order within each branch.
     *
     * @param cur node at which preorder traversal begins
     * @param out destination list in which to append nodes
     */
    private void buildFlatList(TreeNode cur, List<TreeNode> out) {
        out.add(cur);
        if (cur.getChildNodes() != null) {
            for (TreeNode c : cur.getChildNodes()) {
                buildFlatList(c, out);
            }
        }
    }
}
