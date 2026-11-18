package geshra.net.web.ui.components.tree;

import geshra.net.web.ui.components.Div;
import geshra.net.web.ui.components.Span;
import geshra.net.web.ui.css.Display;
import geshra.net.web.ui.css.FontWeight;
import geshra.net.web.ui.css.UserSelect;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;

/**
 * Renders one tree value with a label, expansion caret, and child container. Nodes borrow their containing Tree and participate in its selection model. Mutations run on the owning session event loop and queue browser updates; unattached nodes can be assembled before rendering.
 */
public class TreeNode extends Div {
    /**
     * Keeps keyboard-focused row labels visible without aligning an expanded subtree's full bounds.
     */
    private static final Map<String, String> REVEAL_OPTIONS = Map.of("block", "nearest", "inline", "nearest");

    protected Tree tree; // Owning tree, resolved lazily from ancestors when absent.
    private TreeNode parentNode; // Parent tree node; null for the root.
    private Object value; // Application value represented by the node.
    private ArrayList<TreeNode> children; // Child nodes in display order; null for a leaf.
    private Span caret; // Clickable marker used to expand or collapse children.
    private Div node; // Container for the expansion marker and label.
    private Span text; // Text rendered by this component.
    private Div nested; // Container whose visibility follows expansion state.
    private boolean hidden; // Whether the child container is collapsed.
    private boolean selected; // Whether the label belongs to the current tree selection.

    /**
     * Creates an unattached tree node component; attachment and DOM updates are owned by its session UI.
     *
     * @param value application value or CSS text to retain
     */
    public TreeNode(Object value) {
        super();

        this.hidden = true;
        this.value = value;

        this.nested = new Div();
        this.caret = new Span(" ");
        this.text = new Span(String.valueOf(value));
        this.node = new Div();
    }

    /**
     * Attaches a node under this branch and installs an expansion listener when the first child is added.
     *
     * @param node node supplied to this operation
     */
    public void addNode(TreeNode node) {
        Objects.requireNonNull(node, "node");
        if (node == this || isDescendantOf(node)) throw new IllegalArgumentException("A tree node cannot contain itself or an ancestor");
        if (node.parentNode == this && node.getParent() == null && node.tree == getTree()) {
            TreeNode next = null;
            for (int index = children.indexOf(node) + 1; index < children.size(); index++) {
                TreeNode candidate = children.get(index);
                if (candidate.getParent() == nested) { next = candidate; break; }
            }
            if (next == null) nested.add(node);
            else nested.insertBefore(next, node);
            Tree owner = getTree();
            if (owner != null) owner.configureSubtree(node);
            return;
        }
        if (node.parentNode != null) throw new IllegalArgumentException("A tree node already belongs to a parent");
        if (node.getTree() != null || node.getParent() != null) throw new IllegalArgumentException("A tree node already belongs to a component tree");
        if (children == null) {
            this.children = new ArrayList<>();
            this.caret.setText("+");
            this.caret.addClickListener(ignored -> {
                Tree owner = getTree();
                if (owner != null) owner.focusNode(this, true);
                toggle();
            });
            attribute("aria-expanded", "false");
        }

        node.parentNode = this;
        node.tree = this.tree;

        this.children.add(node);
        this.nested.add(node);
        Tree owner = getTree();
        if (owner != null) owner.configureSubtree(node);
    }

    @Override
    protected void create() {
        super.create();
        attribute("role", "treeitem");
        attribute("class", "geshra-tree-item");
        attribute("tabindex", "-1");
        attribute("data-geshra-keyboard", "tree");
        ariaLabel(String.valueOf(value));
        node.attribute("class", "geshra-tree-label");
        node.attribute("role", "presentation");
        caret.attribute("aria-hidden", "true");
        nested.attribute("role", "group");
        addKeyDownListener(event -> { Tree owner = getTree(); if (owner != null) owner.keyDown(this, event); });
        listen("focus", event -> { Tree owner = getTree(); if (owner != null) owner.nativeFocus(this, true); });
        listen("blur", event -> { Tree owner = getTree(); if (owner != null) owner.nativeFocus(this, false); });

        this.getStyle()
                .margin("0 0 .0 .5em");

        this.node.getStyle()
                .cursor("pointer")
                .display(Display.INLINE_FLEX)
                .margin("0")
                .padding("0");

        this.caret.getStyle()
                .set("display", "inline-flex")
                .set("transform", "rotate(0deg)")
                .width("1em")
                .height("1em")
                .fontWeight(FontWeight.BOLD)
                .margin("0 .25em 0 0")
                .transformOrigin("50% 50%")
                .transition("transform 0.3s ease")
                .userSelect(UserSelect.NONE);

        this.text.getStyle()
                .userSelect(UserSelect.NONE);
        this.text.addClickListener(new TreeSelectionListener(this));

        this.nested.getStyle()
                .marginLeft("1em")
                .padding("0")
                .display(hidden ? Display.NONE : Display.BLOCK);

        this.node.add(caret, text);
        this.add(node, nested);
    }

    @Override
    public TreeNode detach() {
        Tree owner = getTree();
        if (owner != null && getParent() != null) owner.detaching(this);
        super.detach();
        return this;
    }

    @Override
    protected void destroy() {
        Tree owner = getTree();
        TreeNode previous = parentNode;
        if (previous != null && previous.children != null) previous.children.remove(this);
        parentNode = null;
        selected = false;
        if (owner != null) owner.disposedNode(this, previous);
        tree = null;
        if (children != null) {
            for (TreeNode retained : children) {
                retained.parentNode = null;
                retained.assignTree(null);
            }
            children.clear();
        }
        if (previous != null && previous.hasLiveRow() && !previous.hasChildren()) {
            previous.caret.setText(" ");
            previous.removeAttribute("aria-expanded");
            previous.hidden = true;
            previous.nested.getStyle()
                    .display(Display.NONE);
        }
    }

    /**
     * Reports whether the row still belongs to a connected node during recursive destruction.
     * @return whether focus/attribute updates can safely target this row
     */
    boolean hasLiveRow() { return node.getParent() == this && isAttached(); }

    /**
     * Toggles child visibility and the expansion marker, queuing the corresponding style updates.
     */
    public void toggle() {
        setExpanded(hidden);
    }

    /**
     * Expands or collapses a branch. Leaves remain unexpanded and expose no aria-expanded state.
     * A focused descendant is handed to this branch before its group becomes hidden.
     * @param expanded whether children should be visible
     */
    public void setExpanded(boolean expanded) {
        if (!hasChildren() || expanded == !hidden) return;
        Tree owner = getTree();
        if (!expanded && owner != null) owner.collapsing(this);
        caret.setText(expanded ? "-" : "+");
        nested.getStyle()
                .display(expanded ? Display.BLOCK : Display.NONE);
        hidden = !expanded;
        attribute("aria-expanded", String.valueOf(expanded));
    }

    /**
     * Reports visible branch expansion independently of selection.
     * @return whether this non-leaf node's children are visible
     */
    public boolean isExpanded() { return hasChildren() && !hidden; }

    /**
     * Reports whether the node has child branches or leaves.
     * @return whether this is a parent node
     */
    boolean hasChildren() { return children != null && !children.isEmpty(); }

    /**
     * Assigns ownership recursively when a preassembled hierarchy is added to a tree.
     * @param owner containing tree
     */
    void assignTree(Tree owner) {
        tree = owner;
        if (children != null) for (TreeNode child : children) child.assignTree(owner);
    }

    /**
     * Returns this node's logical parent independently of internal rendering containers.
     * @return parent node, or null for a root
     */
    TreeNode getParentNode() { return parentNode; }

    /**
     * Returns the first connected child, omitting retained detached subtrees.
     * @return first connected child, or null if no child is currently attached
     */
    TreeNode firstVisibleChild() {
        if (children != null) for (TreeNode child : children) if (child.isAttached()) return child;
        return null;
    }

    /**
     * Tests whether this node belongs below a particular branch.
     * @param ancestor candidate ancestor
     * @return whether the candidate occurs in the logical parent chain
     */
    boolean isDescendantOf(TreeNode ancestor) {
        for (TreeNode parent = parentNode; parent != null; parent = parent.parentNode) if (parent == ancestor) return true;
        return false;
    }

    /**
     * Finds the next visible node without allocating a flattened hierarchy.
     * @return next node in expanded preorder, or null at the end
     */
    TreeNode nextVisible() {
        if (isExpanded()) for (TreeNode child : children) if (child.isAttached()) return child;
        TreeNode current = this;
        while (current.parentNode != null) {
            List<TreeNode> siblings = current.parentNode.children;
            int index = siblings.indexOf(current);
            for (int next = index + 1; next < siblings.size(); next++) if (siblings.get(next).isAttached()) return siblings.get(next);
            current = current.parentNode;
        }
        return null;
    }

    /**
     * Finds the preceding visible node while skipping collapsed descendants.
     * @return preceding node, or null for the visible root
     */
    TreeNode previousVisible() {
        if (parentNode == null) return null;
        int index = parentNode.children.indexOf(this);
        for (int previous = index - 1; previous >= 0; previous--) if (parentNode.children.get(previous).isAttached()) return parentNode.children.get(previous)
                .lastVisible();
        return parentNode;
    }

    /**
     * Finds the deepest final visible descendant of this node.
     * @return this node or its final expanded descendant
     */
    TreeNode lastVisible() {
        TreeNode current = this;
        while (current.isExpanded()) {
            TreeNode last = null;
            for (int index = current.children.size() - 1; index >= 0; index--) if (current.children.get(index).isAttached()) { last = current.children.get(index); break; }
            if (last == null) break;
            current = last;
        }
        return current;
    }

    /**
     * Reveals the label row after native focus; expanded descendants must not determine scrolling.
     */
    void revealLabel() { node.callMethod("scrollIntoView", REVEAL_OPTIONS); }

    /**
     * Updates the value and queues the corresponding browser change.
     *
     * @param value application value or CSS text to retain
     */
    public void setValue(Object value) {
        this.value = value;
        this.text.setText(String.valueOf(value));
        ariaLabel(String.valueOf(value));
    }

    /**
     * Returns the tree retained by this instance; null means no associated value.
     * @return the resulting tree value
     */
    public Tree getTree() {
        if (tree == null) {
            TreeNode parent = this.parentNode;
            while (parent != null) {
                if (parent.getTree() != null) {
                    tree = parent.getTree();
                    break;
                }
                parent = parent.parentNode;
            }
        }
        return tree;
    }

    /**
     * Returns the value retained by this instance.
     * @return the resulting value value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Reports whether this node belongs to the current tree selection.
     *
     * @return whether the node is selected
     */
    boolean isSelected() {
        return selected;
    }

    /**
     * Changes selection state and queues the corresponding label background.
     *
     * @param selected whether to highlight the label
     */
    void setSelected(boolean selected) {
        this.selected = selected;
        text.getStyle()
                .backgroundColor(selected ? "Highlight" : "transparent")
                .color(selected ? "HighlightText" : "inherit");
        Tree owner = getTree();
        if (owner != null && owner.getSelectionMode() != TreeSelectionMode.NONE) attribute("aria-selected", String.valueOf(selected));
    }

    /**
     * Exposes selected state only for nodes selectable under the current tree policy.
     * @param selectable whether selection is enabled
     */
    void updateSelectionSemantics(boolean selectable) {
        if (selectable) attribute("aria-selected", String.valueOf(selected));
        else removeAttribute("aria-selected");
    }

    /**
     * Exposes child nodes to package-local selection traversal without copying.
     *
     * @return children in display order, or null for a leaf
     */
    List<TreeNode> getChildNodes() {
        return children;
    }
}
