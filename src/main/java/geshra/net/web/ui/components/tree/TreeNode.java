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
 * Renders one tree value with a label, expansion caret, and child container. Nodes borrow their containing Tree and participate in its selection model. Mutations run on the owning session event loop and queue browser updates; unattached nodes can be assembled before rendering.
 */
public class TreeNode extends Div {

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
        this.text = new Span();
        this.node = new Div();
    }

    /**
     * Attaches a node under this branch and installs an expansion listener when the first child is added.
     *
     * @param node node supplied to this operation
     */
    public void addNode(TreeNode node) {
        if (children == null) {
            this.children = new ArrayList<>();
            this.caret.setText("+");
            this.caret.addClickListener(ignored -> toggle());
        }

        node.parentNode = this;
        node.tree = this.tree;

        this.children.add(node);
        this.nested.add(node);
    }

    @Override
    protected void create() {
        super.create();

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

        this.text.queueForDispatch(DOMUpdateType.SET_TEXT, DOMUpdateParam.TEXT, String.valueOf(value));
        this.text.getStyle()
                .userSelect(UserSelect.NONE);
        this.text.addClickListener(new TreeSelectionListener(this));

        this.nested.getStyle()
                .marginLeft("0 0 0 1em")
                .padding("0")
                .display(Display.NONE);

        this.node.add(caret, text);
        this.add(node, nested);
    }

    /**
     * Toggles child visibility and the expansion marker, queuing the corresponding style updates.
     */
    public void toggle() {
        caret.setText(hidden ? "-" : "+");
        nested.getStyle()
                .display(hidden ? Display.BLOCK : Display.NONE);

        hidden = !hidden;
    }

    /**
     * Updates the value and queues the corresponding browser change.
     *
     * @param value application value or CSS text to retain
     */
    public void setValue(Object value) {
        this.value = value;
        this.queueForDispatch(DOMUpdateType.SET_TEXT, DOMUpdateParam.TEXT, String.valueOf(value));
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
                .backgroundColor(selected ? "gray" : "transparent");
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
