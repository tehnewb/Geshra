package geshra.net.web.ui.components.tree;

import geshra.net.web.ui.components.Div;
import geshra.net.web.ui.Component;
import geshra.net.web.ui.event.KeyDownEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Iterator;

/**
 * Coordinates a hierarchical, keyboard-reachable tree with server-owned selection and one roving
 * tab stop. Arrow keys move through visible nodes, Right/Left expand or collapse branches, and
 * Home/End reach visible endpoints. Focus never implicitly changes selection. Space toggles a node
 * in MULTIPLE mode; Enter and ordinary pointer clicks select it exclusively. Ctrl/Meta-click toggles
 * and Shift-click/Shift-Space select an inclusive visible range. NONE keeps navigation available but
 * removes selection semantics; changing to SINGLE retains at most the first selected node.
 *
 * <p>All operations run on the owning UI session thread. TreeNode owns its label, caret and group;
 * this coordinator borrows those nodes and owns the selected-node deque. No per-frame task exists.
 * Arrow traversal allocates no flattened tree; range selection allocates only its requested range.
 * Native focus events synchronize the roving position so collapsing a focused descendant hands focus
 * to its parent without stealing focus from another page control. Typeahead and optional select-all
 * shortcuts are not implemented.</p>
 *
 * <pre>{@code
 * TreeNode root = new TreeNode("Project");
 * root.addNode(new TreeNode("README.md"));
 * Tree tree = new Tree(root);
 * tree.ariaLabel("Project files");
 * tree.setSelectionMode(TreeSelectionMode.SINGLE);
 * ui.add(tree);
 * }</pre>
 */
public class Tree extends Div {
    private TreeSelectionMode selectionMode = TreeSelectionMode.MULTIPLE; // Current selection cardinality policy.
    private ArrayDeque<TreeNode> selection; // Lazily allocated selected nodes in selection order.
    private TreeNode root; // Root node; null represents an empty tree.
    private TreeNode focusedNode; // Current roving tab stop, independent of selection.
    private boolean containsFocus; // Whether a native tree-item focus event is still active.

    /**
     * Creates an empty named tree, which remains keyboard-focusable without a root node.
     */
    public Tree() {
        attribute("role", "tree");
        attribute("class", "geshra-tree");
        ariaLabel("Tree");
        attribute("aria-multiselectable", "true");
        attribute("tabindex", "0");
        listen("focus", event -> {
            containsFocus = true;
            if (root != null && root.isAttached()) focusNode(root, true);
        });
        listen("blur", event -> { if (focusedNode == null) containsFocus = false; });
    }

    /**
     * Creates a tree owning a root hierarchy; mutable nodes must not be shared across sessions.
     * @param root non-null root node
     */
    public Tree(TreeNode root) {
        this();
        this.root = Objects.requireNonNull(root, "root");
        if (root.getTree() != null || root.getParent() != null) throw new IllegalArgumentException("A root node already belongs to a component tree");
        root.assignTree(this);
    }

    @Override
    protected void create() {
        if (root == null) return;
        attribute("tabindex", "-1");
        add(root);
        configureSubtree(root);
        focusedNode = root;
        root.attribute("tabindex", "0");
    }

    @Override
    public Tree add(Component... children) {
        super.add(children);
        if (root != null && root.getParent() == this && root.isAttached()) {
            attribute("tabindex", "-1");
            configureSubtree(root);
            if (focusedNode == null || !focusedNode.isAttached()) focusNode(root, containsFocus);
        }
        return this;
    }

    @Override
    protected void destroy() {
        if (selection != null) selection.clear();
        focusedNode = null;
        containsFocus = false;
    }

    /**
     * Updates selection cardinality and ARIA together. NONE clears selection; SINGLE retains the
     * first selected node when transitioning from MULTIPLE. Keyboard focus remains unchanged.
     * @param selectionMode non-null selection policy
     */
    public void setSelectionMode(TreeSelectionMode selectionMode) {
        this.selectionMode = Objects.requireNonNull(selectionMode, "selectionMode");
        if (selection != null) {
            if (selectionMode == TreeSelectionMode.NONE) clearSelection();
            else if (selectionMode == TreeSelectionMode.SINGLE) {
                while (selection.size() > 1) selection.removeLast()
                        .setSelected(false);
            }
        }
        attribute("aria-multiselectable", String.valueOf(selectionMode == TreeSelectionMode.MULTIPLE));
        if (root != null) configureSubtree(root);
    }

    /**
     * Returns the current selection cardinality policy.
     * @return non-null selection mode
     */
    public TreeSelectionMode getSelectionMode() { return selectionMode; }

    /**
     * Returns the session-owned selection deque. Prefer reading it without mutation so ARIA and
     * highlighting remain coordinated through pointer and keyboard selection operations.
     * @return selected nodes in selection order
     */
    public ArrayDeque<TreeNode> getSelection() {
        if (selection == null) selection = new ArrayDeque<>();
        return selection;
    }

    /**
     * Returns the root of this hierarchy.
     * @return root node, or null for an empty tree
     */
    public TreeNode getRoot() { return root; }

    /**
     * Applies current tree ownership and selection semantics to a newly attached branch.
     * @param node branch root
     */
    void configureSubtree(TreeNode node) {
        node.tree = this;
        node.updateSelectionSemantics(selectionMode != TreeSelectionMode.NONE);
        if (node.getChildNodes() != null) for (TreeNode child : node.getChildNodes()) configureSubtree(child);
    }

    /**
     * Updates the unique roving tab stop and optionally moves native focus to it.
     * @param node node belonging to this tree
     * @param moveFocus whether to call the native focus method
     */
    void focusNode(TreeNode node, boolean moveFocus) {
        if (node == null || node.getTree() != this || !node.isAttached()) return;
        if (focusedNode != node) {
            if (focusedNode != null) focusedNode.attribute("tabindex", "-1");
            focusedNode = node;
            node.attribute("tabindex", "0");
        }
        if (moveFocus) {
            node.callMethod("focus");
            node.revealLabel();
        }
    }

    /**
     * Synchronizes actual native focus without generating another focus call.
     * @param node event target
     * @param focused whether it received focus rather than blur
     */
    void nativeFocus(TreeNode node, boolean focused) {
        if (focused) {
            focusNode(node, false);
            containsFocus = true;
        } else if (focusedNode == node) containsFocus = false;
    }

    /**
     * Moves a roving descendant to the closing branch, focusing only when the tree owns DOM focus.
     * @param node branch being collapsed
     */
    void collapsing(TreeNode node) {
        if (focusedNode != null && focusedNode != node && focusedNode.isDescendantOf(node)) focusNode(node, containsFocus);
    }

    /**
     * Removes a retained subtree from selection and hands off its roving position before detach.
     * Reattaching preserves node identity and expansion, but selection stays cleared.
     * @param node retained subtree being disconnected
     */
    void detaching(TreeNode node) {
        if (selection != null) {
            Iterator<TreeNode> iterator = selection.iterator();
            while (iterator.hasNext()) {
                TreeNode selected = iterator.next();
                if (selected == node || selected.isDescendantOf(node)) { selected.setSelected(false); iterator.remove(); }
            }
        }
        if (focusedNode == null || (focusedNode != node && !focusedNode.isDescendantOf(node))) return;
        focusedNode.attribute("tabindex", "-1");
        focusedNode = null;
        TreeNode parent = node.getParentNode();
        while (parent != null && !parent.hasLiveRow()) parent = parent.getParentNode();
        if (parent != null) focusNode(parent, containsFocus);
        else {
            attribute("tabindex", "0");
            if (containsFocus) callMethod("focus");
        }
    }

    /**
     * Releases references after one node's native row has been disposed. Only live ancestor rows
     * receive updates; recursive whole-tree destruction never addresses already-disposed controls.
     * @param node removed node
     * @param parent previous logical parent
     */
    void disposedNode(TreeNode node, TreeNode parent) {
        if (selection != null) selection.remove(node);
        if (focusedNode == node) {
            focusedNode = null;
            while (parent != null && !parent.hasLiveRow()) parent = parent.getParentNode();
            if (parent != null) focusNode(parent, containsFocus);
        }
        if (root == node) {
            root = null;
            focusedNode = null;
            attribute("tabindex", "0");
            if (containsFocus) callMethod("focus");
        }
    }

    /**
     * Applies the shared pointer/keyboard selection policy with a visible-node range.
     * @param node target node
     * @param toggle whether MULTIPLE mode toggles rather than replacing selection
     * @param range whether to extend from the last selected node through visible preorder
     */
    void selectNode(TreeNode node, boolean toggle, boolean range) {
        if (selectionMode == TreeSelectionMode.NONE || node.getTree() != this || !node.isAttached()) return;
        ArrayDeque<TreeNode> selected = getSelection();
        if (selectionMode == TreeSelectionMode.MULTIPLE && toggle) {
            node.setSelected(!node.isSelected());
            if (node.isSelected()) selected.add(node);
            else selected.remove(node);
            return;
        }
        if (selectionMode == TreeSelectionMode.MULTIPLE && range && !selected.isEmpty()) {
            TreeNode anchor = selected.peekLast();
            ArrayList<TreeNode> interval = new ArrayList<>();
            boolean collecting = false;
            int found = 0;
            for (TreeNode current = root; current != null; current = current.nextVisible()) {
                if (current == anchor) found |= 1;
                if (current == node) found |= 2;
                if (current == anchor || current == node) {
                    if (collecting || anchor == node) { interval.add(current); break; }
                    collecting = true;
                }
                if (collecting) interval.add(current);
            }
            if (found == 3) {
                clearSelection();
                for (TreeNode current : interval) { current.setSelected(true); selected.add(current); }
                return;
            }
        }
        clearSelection();
        node.setSelected(true);
        selected.add(node);
    }

    /**
     * Clears selection and its corresponding visual/ARIA states.
     */
    private void clearSelection() {
        if (selection == null) return;
        for (TreeNode node : selection) node.setSelected(false);
        selection.clear();
    }

    /**
     * Handles visible preorder navigation and activation without conflating focus with selection.
     * @param node focused event target
     * @param event trusted typed key event forwarded by the owning browser widget
     */
    void keyDown(TreeNode node, KeyDownEvent event) {
        if (event.getKey() == null || event.isAltPressed() || event.isMetaPressed()) return;
        switch (event.getKey()) {
            case ARROW_DOWN -> focusNode(node.nextVisible(), true);
            case ARROW_UP -> focusNode(node.previousVisible(), true);
            case HOME -> focusNode(root, true);
            case END -> focusNode(root == null ? null : root.lastVisible(), true);
            case ARROW_RIGHT -> {
                if (node.hasChildren()) {
                    if (!node.isExpanded()) node.setExpanded(true);
                    else focusNode(node.firstVisibleChild(), true);
                }
            }
            case ARROW_LEFT -> {
                if (node.isExpanded()) node.setExpanded(false);
                else focusNode(node.getParentNode(), true);
            }
            case SPACE -> { if (!event.isRepeated()) selectNode(node, selectionMode == TreeSelectionMode.MULTIPLE && !event.isShiftPressed(), event.isShiftPressed()); }
            case ENTER -> { if (!event.isRepeated()) selectNode(node, false, false); }
            default -> { }
        }
    }
}
