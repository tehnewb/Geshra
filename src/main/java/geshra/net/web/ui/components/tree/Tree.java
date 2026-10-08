package geshra.net.web.ui.components.tree;

import geshra.net.web.ui.components.Div;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;

/**
 * {@code Tree<T>}
 * <p>
 * A hierarchical tree view rendered as a nested HTML list, using inline styles
 * instead of CSS classes. Supports branches and leaves, with expand/collapse
 * behavior controlled via Component.getStyle(). Includes a caret (U+276F) which
 * rotates upon expansion/collapse.
 * <p>
 * Now generic in the node-value type T; by default uses T::toString as the label,
 * but you can supply your own labelResolver if you need.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 15th, 2025
 */
public class Tree extends Div {

    private TreeSelectionMode selectionMode = TreeSelectionMode.MULTIPLE; // Policy controlling allowed selection cardinality.
    private ArrayDeque<TreeNode> selection; // Selected entries in selection order.

    private TreeNode root; // Root node; null represents an empty tree.

    /**
     * Creates an unattached tree component; attachment and DOM updates are owned by its session UI.
     */
    public Tree() {
        super();
    }

    /**
     * Creates an unattached tree component; attachment and DOM updates are owned by its session UI.
     *
     * @param root root node to attach
     */
    public Tree(TreeNode root) {
        super();
        root.tree = this;
        this.root = root;
    }

    @Override
    protected void create() {
        if (this.root != null) {
            this.add(root);
        }
    }

    @Override
    protected void destroy() {

    }

    /**
     * Updates the selection mode used by subsequent operations.
     *
     * @param selectionMode selection policy; must not be null
     */
    public void setSelectionMode(TreeSelectionMode selectionMode) {
        this.selectionMode = selectionMode;
    }

    /**
     * Returns the selection mode retained by this instance.
     * @return the resulting selection mode value
     */
    public TreeSelectionMode getSelectionMode() {
        return selectionMode;
    }

    /**
     * Returns the selection retained by this instance.
     * @return the resulting selection value
     */
    public ArrayDeque<TreeNode> getSelection() {
        if (selection == null)
            selection = new ArrayDeque<>();
        return selection;
    }

    /**
     * Returns the root retained by this instance; null means no associated value.
     * @return the resulting root value
     */
    public TreeNode getRoot() {
        return root;
    }
}
