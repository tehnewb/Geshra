package geshra.net.web.ui;

import geshra.event.Event;
import geshra.event.EventHandler;
import geshra.event.EventPublisher;
import geshra.event.EventType;
import geshra.event.EventTypes;
import geshra.net.web.SessionContext;
import geshra.net.web.TextPacketEncoder;
import geshra.net.web.ui.css.Style;
import geshra.net.web.ui.css.StyleChangeEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import geshra.net.web.ui.event.*;
import geshra.net.web.ui.event.DomEvent;

/**
 * Represents a generic UI component in a hierarchical structure. This is an abstract class
 * that provides base functionality for creating and managing custom components.
 * Components are constructed with a specific HTML tag and can be used to build complex
 * UI structures by arranging child components within parent components.
 * <p>
 * Each component is associated with a unique identifier and a {@link Style} object
 * to allow dynamic styling updates. The class manages the lifecycle of components
 * by providing methods to add and remove child components and offers mechanisms for
 * event handling and DOM updates.
 * <p>
 * Components must be subclassed, and their lifecycle methods {@code create} and {@code destroy}
 * must be implemented to define specific behaviors during the component's initialization
 * and cleanup phases, respectively.
 * <p>
 * This class also facilitates event-based interactions, allowing components to listen
 * for and handle various events, such as style changes, value changes, and user input events.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 2nd, 2025
 */
public abstract class Component implements EventHandler<StyleChangeEvent> {

    private final DOMDispatcher dispatcher = new DOMDispatcher(this); // Pending mutations owned by this component.
    Component nextDirty; // Intrusive next pointer in the owning UI's pending component list.
    boolean dirtyQueued; // Whether this component is already linked into that list.
    private ArrayList<DomEventBinding> domBindings; // Lazily allocated native browser subscriptions.
    private boolean retainedDom; // Whether a detached browser subtree is retained for reattachment.
    private boolean created; // Whether initial attachment hooks have already run.

    private ArrayList<Component> children; // Holds child components nested under this component.

    private EventPublisher publisher; // Responsible for managing the publishing and subscription of events within the component.

    private Style style; // Manages CSS styles for this component, identified by its component ID.

    private UI ui; // Reference to the top-level UI context this component belongs to.

    private final String tag; // The HTML tag name used to render this component (e.g., "div", "span", "button").

    private Component parent; // Parent component in the hierarchy, or null if this is a root UI.

    private int componentID = -1; // Unique identifier assigned by the UI for DOM mapping.
    private boolean attached; // True if this component is attached to the live DOM; false otherwise.

    private boolean enabled = true; // Represents the native default enabled state.

    /**
     * Constructs a new Component with the given HTML tag.
     * <p>
     * If this instance <em>is</em> a {@link UI}, it serves as its own context;
     * otherwise it retrieves the current UI from the {@link SessionContext} and
     * gets a new component ID. Initializes a {@link Style} object scoped to
     * the generated component ID.
     *
     * @param tag the HTML tag name to render for this component
     */
    public Component(String tag) {
        this.tag = tag;
        if (this instanceof UI ui) {
            this.componentID = 0;
            this.ui = ui;
        }
    }

    /**
     * Lifecycle hook invoked when the component is added to its parent.
     * <p>
     * Subclasses should override this method to perform initialization logic,
     * such as setting up child elements or default event listeners.
     */
    protected abstract void create();

    /**
     * Lifecycle hook invoked when the component is removed from its parent.
     * <p>
     * Subclasses should override this method to perform cleanup logic,
     * such as releasing resources or unsubscribing from services.
     */
    protected abstract void destroy();

    /**
     * Adds one or more child parts to this component.
     * <p>
     * Each child is registered with the UI context, queued for a DOM append update,
     * initialized via {@link #create()}, and marked as attached if this component
     * is currently attached. Finally, all queued updates (including nested children)
     * are flushed to the client.
     *
     * @param children one or more parts to append as children
     */
    public Component add(Component... children) {
        UI owner = getUI();
        owner.beginUpdate();
        try {
            if (this.children == null) this.children = new ArrayList<>();

            for (Component child : children) {
                validateChild(child);
                if (child.parent == this && this.children.getLast() == child) continue;
                boolean move = prepareChild(child);
                this.getUI()
                        .register(child.getComponentID(), child);
                this.children.add(child);
                this.queueForDispatch(move ? DOMUpdateType.MOVE_CHILD : DOMUpdateType.APPEND_CHILD, DOMUpdateParam.IDENTIFIER, child.getComponentID(), DOMUpdateParam.HTML, child.tag);
                child.parent = this;
                child.attached = this.isAttached();
                child.retainedDom = false;
                child.markPendingSubtree();
                if (!child.created) { child.created = true; child.create(); }
                child.markPendingSubtree();
            }
            push();
            return this;
        } finally {
            owner.endUpdate();
        }
    }

    /**
     * Inserts a new component immediately before an existing child and flushes queued updates. If the
     * reference child is absent, appends the new component instead.
     *
     * @param child existing child used as the insertion reference
     * @param toInsert new component to insert
     * @return this parent component
     */
    public Component insertBefore(Component child, Component toInsert) {
        validateChild(toInsert);
        if (child == toInsert) return this;
        if (this.children == null) {
            this.children = new ArrayList<>();
        }

        // find the index of the reference child
        int idx = this.children.indexOf(child);
        if (idx < 0)
            return this.add(toInsert);

        boolean move = prepareChild(toInsert);
        idx = this.children.indexOf(child);
        // Register the new component with the UI and our tree.
        this.getUI()
                .register(toInsert.getComponentID(), toInsert);
        this.children.add(idx, toInsert);

        this.queueForDispatch(move ? DOMUpdateType.MOVE_CHILD : DOMUpdateType.INSERT_BEFORE, DOMUpdateParam.INSERT_ID, child.getComponentID(), DOMUpdateParam.IDENTIFIER, toInsert.getComponentID(), DOMUpdateParam.HTML, toInsert.tag);

        toInsert.parent = this;
        toInsert.attached = this.isAttached();
        toInsert.retainedDom = false;
        toInsert.markPendingSubtree();
        if (!toInsert.created) { toInsert.created = true; toInsert.create(); }
        toInsert.markPendingSubtree();

        push();
        return this;
    }

    /**
     * Rejects cross-session parenting and cycles, matching native hierarchy constraints.
     * @param child requested child
     */
    private void validateChild(Component child) {
        Objects.requireNonNull(child, "child");
        if (child.getUI() != getUI()) throw new IllegalArgumentException("Components must belong to the same UI");
        for (Component ancestor = this; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == child) throw new IllegalArgumentException("A component cannot contain an ancestor");
        }
    }

    /**
     * Unlinks a prior parent and reports whether the native node already exists.
     * @param child child to move
     * @return whether to move a retained native node
     */
    private boolean prepareChild(Component child) {
        if (child.isAttached() && !isAttached()) child.detach();
        boolean move = child.retainedDom || child.isAttached();
        if (move) getUI()
                .flushUpdates();
        if (child.parent != null) {
            child.parent.children.remove(child);
            if (!move) child.parent.dispatcher.cancelCreation(child.getComponentID());
        }
        getUI()
                .releaseRetained(child);
        return move;
    }

    /**
     * Detaches this subtree while retaining native nodes, IDs, values, and listeners.
     * Reattach it with add or insertBefore. Call dispose when it is no longer needed.
     * @return this component
     */
    public Component detach() {
        if (parent == null) return this;
        Component previous = parent;
        boolean rendered = isAttached();
        if (rendered) getUI()
                .flushUpdates();
        previous.children.remove(this);
        if (rendered) {
            previous.dispatcher.queue(new DOMUpdate(DOMUpdateType.DETACH, getComponentID()));
            retainedDom = true;
            getUI()
                    .retain(this);
        } else previous.dispatcher.cancelCreation(getComponentID());
        parent = null;
        attached = false;
        previous.push();
        return this;
    }

    /**
     * Permanently removes this subtree and its session registrations.
     */
    public void dispose() {
        if (parent != null) { parent.remove(this); return; }
        if (this instanceof UI) throw new IllegalStateException("Clear the UI instead");
        UI owner = getUI();
        if (retainedDom) {
            owner.queueNativeRemoval(getComponentID());
        }
        disposeSubtree(owner);
        owner.flushPendingUpdates();
    }

    /**
     * Releases a subtree in one traversal without sending redundant descendant removals.
     * @param owner registry owning these components
     */
    private void disposeSubtree(UI owner) {
        if (children != null) {
            for (Component child : children) child.disposeSubtree(owner);
            children.clear();
        }
        destroy();
        dispatcher.clear();
        owner.releaseRetained(this);
        owner.recycle(componentID);
        componentID = -1;
        ui = null;
        parent = null;
        attached = false;
        retainedDom = false;
        created = false;
    }

    /**
     * Queues one root removal without redundant descendant commands.
     * @param identifier removed root ID
     */
    void queueNativeRemoval(int identifier) { dispatcher.queue(new DOMUpdate(DOMUpdateType.REMOVE, identifier)); }

    /**
     * Removes one or more child parts from this component.
     * <p>
     * Each child is detached, queued for a DOM remove update, recycled in the UI,
     * and cleaned up via {@link #destroy()}. Finally, all queued updates
     * (including nested children) are flushed to the client.
     *
     * @param children one or more parts to remove
     */
    public void remove(Component... children) {
        if (this.children == null) return;
        UI owner = getUI();
        owner.beginUpdate();
        try {

            for (Component child : children) {
                if (child.parent != this) continue;
                if (!isAttached() && !retainedDom) dispatcher.cancelCreation(child.getComponentID());
                this.dispatcher.queue(new DOMUpdate(DOMUpdateType.REMOVE, child.getComponentID()));
                int last = this.children.size() - 1;
                if (last >= 0 && this.children.get(last) == child) this.children.removeLast();
                else this.children.remove(child);
                child.disposeSubtree(getUI());
            }
            push();
        } finally {
            owner.endUpdate();
        }
    }

    /**
     * Handles a {@link StyleChangeEvent} by queuing a corresponding DOM style update.
     * <p>
     * Called automatically when the {@link Style} object fires change events.
     *
     * @param event the style change event containing the property and new value
     */
    @Override
    public void handle(StyleChangeEvent event) {
        this.dispatcher.queue(new DOMUpdate(DOMUpdateType.SET_STYLE, getComponentID()).param(DOMUpdateParam.STYLE_PROPERTY, event.getProperty()).param(DOMUpdateParam.STYLE_VALUE, event.getValue()));
        push();
    }

    /**
     * Registers an event listener for a specific event type and binds it to a DOM event.
     * <p>
     * If not already registered, queues a DOM update to add the corresponding browser
     * event listener. Then registers the listener with the internal {@link EventPublisher}.
     *
     * @param <T>      the event subtype
     * @param type     the typed numeric event route
     * @param listener the listener to notify when the event occurs
     * @param name     the DOM event name (e.g., "click", "keydown")
     */
    protected <T extends Event> void registerEventListener(EventType<T> type, EventHandler<T> listener, String name) {
        registerEventListener(type, 0, listener, name);
    }

    /**
     * Registers a DOM event handler with an explicit execution priority.
     *
     * @param type typed numeric event route
     * @param priority execution priority; higher values run first
     * @param listener handler receiving the event
     * @param name browser event name
     * @param <T> event type
     */
    protected <T extends Event> void registerEventListener(EventType<T> type, int priority, EventHandler<T> listener, String name) {
        if (this.publisher == null) this.publisher = new EventPublisher();

        boolean firstHandler = !this.publisher.isRegistered(type);
        if (this.publisher.register(type, priority, listener) && firstHandler)
            this.dispatcher.queue(new DOMUpdate(DOMUpdateType.ADD_EVENT_LISTENER, getComponentID()).param(DOMUpdateParam.EVENT_NAME, name).param(DOMUpdateParam.VALUE, "{\"typed\":true}"));

        push();
    }

    /**
     * Publishes the specified event to all registered listeners for processing.
     */
    public void publish(Event event) {
        if (this.publisher != null) this.publisher.publish(event);
        else event.unconsume();
    }

    /**
     * Registers a server-only handler without adding a browser listener.
     * @param type typed route
     * @param priority larger priorities execute first
     * @param handler handler receiving that route
     * @param <T> event payload type
     */
    protected <T extends Event> void registerEventHandler(EventType<T> type, int priority, EventHandler<T> handler) {
        if (publisher == null) publisher = new EventPublisher();
        publisher.register(type, priority, handler);
    }

    /**
     * Subscribes to a native browser event, preserving its case-sensitive name.
     * @param name event name such as focus, change, close, or toggle
     * @param handler server callback
     * @return this component
     */
    public Component listen(String name, EventHandler<DomEvent> handler) { return listen(name, false, handler); }

    /**
     * Subscribes with an explicit synchronous browser cancellation policy.
     * @param name native event name
     * @param preventDefault cancel the default action before sending the event
     * @param handler server callback
     * @return this component
     */
    public Component listen(String name, boolean preventDefault, EventHandler<DomEvent> handler) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(handler, "handler");
        if (domBindings == null) domBindings = new ArrayList<>(2);
        for (DomEventBinding binding : domBindings) {
            if (binding.name().equals(name) && binding.delegate() == handler) return this;
        }
        DomEventBinding binding = new DomEventBinding(name, handler, preventDefault);
        domBindings.add(binding);
        registerEventHandler(EventTypes.DOM, 0, binding);
        for (DomEventBinding other : domBindings) {
            if (other.name().equals(name)) preventDefault |= other.preventDefault();
        }
        queueForDispatch(DOMUpdateType.ADD_EVENT_LISTENER, DOMUpdateParam.EVENT_NAME, name, DOMUpdateParam.VALUE, "{\"generic\":true,\"preventDefault\":" + preventDefault + "}");
        push();
        return this;
    }

    /**
     * Removes one exact native subscription and its browser forwarding when no handlers remain.
     * @param name native event name
     * @param handler exact handler object
     */
    public void removeListener(String name, EventHandler<DomEvent> handler) {
        if (domBindings == null) return;
        for (int i = 0; i < domBindings.size(); i++) {
            DomEventBinding binding = domBindings.get(i);
            if (binding.name().equals(name) && binding.delegate() == handler) {
                domBindings.remove(i);
                publisher.unregister(EventTypes.DOM, binding);
                boolean remaining = false;
                boolean preventDefault = false;
                for (DomEventBinding other : domBindings) {
                    if (other.name().equals(name)) { remaining = true; preventDefault |= other.preventDefault(); }
                }
                if (!remaining) queueForDispatch(DOMUpdateType.REMOVE_EVENT_LISTENER, DOMUpdateParam.EVENT_NAME, name, DOMUpdateParam.VALUE, "generic");
                else queueForDispatch(DOMUpdateType.ADD_EVENT_LISTENER, DOMUpdateParam.EVENT_NAME, name, DOMUpdateParam.VALUE, "{\"generic\":true,\"preventDefault\":" + preventDefault + "}");
                push();
                return;
            }
        }
    }

    /**
     * Assigns a native DOM property using its actual JSON type instead of string coercion.
     * @param property native property name
     * @param value JSON-compatible value
     * @return this component
     */
    public Component setProperty(String property, Object value) {
        queueForDispatch(DOMUpdateType.SET_TYPED_PROPERTY, DOMUpdateParam.KEY, property, DOMUpdateParam.VALUE, BrowserJson.encode(value));
        push();
        return this;
    }

    /**
     * Requests a native element method. Browser promises and errors remain asynchronous.
     * @param method native method name
     * @param arguments JSON-compatible arguments
     * @return this component
     */
    public Component callMethod(String method, Object... arguments) {
        queueForDispatch(DOMUpdateType.CALL_METHOD, DOMUpdateParam.KEY, method, DOMUpdateParam.VALUE, BrowserJson.encode(arguments));
        push();
        return this;
    }

    /**
     * Removes an attribute using removeAttribute rather than assigning the string null.
     * @param name attribute name
     * @return this component
     */
    public Component removeAttribute(String name) {
        queueForDispatch(DOMUpdateType.REMOVE_ATTRIBUTE, DOMUpdateParam.KEY, name);
        push();
        return this;
    }

    /**
     * Adds an attribute and returns this component for convenient construction.
     * @param name attribute name
     * @param value attribute value
     * @return this component
     */
    public Component attribute(String name, String value) { setAttribute(name, value); return this; }

    /**
     * Sets the native accessibility label.
     * @param label accessible name
     * @return this component
     */
    public Component ariaLabel(String label) { return attribute("aria-label", label); }

    /**
     * Attaches a listener for value-change events (e.g., input fields).
     *
     * @param listener the listener to invoke on value changes
     */
    public void addValueChangeListener(EventHandler<ValueChangeEvent> listener) {
        registerEventListener(EventTypes.VALUE_CHANGE, listener, "input");
    }

    /**
     * Registers a value-change handler with explicit priority.
     * @param priority larger values execute first
     * @param listener value-change handler
     */
    public void addValueChangeListener(int priority, EventHandler<ValueChangeEvent> listener) {
        registerEventListener(EventTypes.VALUE_CHANGE, priority, listener, "input");
    }

    /**
     * Attaches a listener for key-up events.
     *
     * @param listener the listener to invoke on key-up actions
     */
    public void addKeyUpListener(EventHandler<KeyUpEvent> listener) {
        registerEventListener(EventTypes.KEY_UP, listener, "keyup");
    }

    /**
     * Registers a key-up handler with explicit priority.
     * @param priority larger values execute first
     * @param listener key-up handler
     */
    public void addKeyUpListener(int priority, EventHandler<KeyUpEvent> listener) {
        registerEventListener(EventTypes.KEY_UP, priority, listener, "keyup");
    }

    /**
     * Attaches a listener for key-down events.
     *
     * @param listener the listener to invoke on key-down actions
     */
    public void addKeyDownListener(EventHandler<KeyDownEvent> listener) {
        registerEventListener(EventTypes.KEY_DOWN, listener, "keydown");
    }

    /**
     * Registers a key-down handler with explicit priority.
     * @param priority larger values execute first
     * @param listener key-down handler
     */
    public void addKeyDownListener(int priority, EventHandler<KeyDownEvent> listener) {
        registerEventListener(EventTypes.KEY_DOWN, priority, listener, "keydown");
    }

    /**
     * Attaches a listener for click events.
     *
     * @param listener the listener to invoke on click actions
     */
    public void addClickListener(EventHandler<ClickEvent> listener) {
        registerEventListener(EventTypes.CLICK, listener, "click");
    }

    /**
     * Registers a click handler with explicit priority.
     * @param priority larger values execute first
     * @param listener click handler
     */
    public void addClickListener(int priority, EventHandler<ClickEvent> listener) {
        registerEventListener(EventTypes.CLICK, priority, listener, "click");
    }

    /**
     * Flushes all queued {@link DOMUpdate DOMUpdates} to the client channel
     * if this component is attached. Recursively invokes {@code push()}
     * on all child components to ensure nested updates are sent.
     */
    public void push() {
        if (isAttached()) getUI()
                .flushPendingUpdates();
    }

    /**
     * Links attached work into the UI's allocation-free pending list.
     */
    void updatesQueued() {
        if (isAttached()) getUI()
                .markDirty(this);
    }

    /**
     * Marks pre-attachment mutations once a subtree becomes visible.
     */
    private void markPendingSubtree() {
        if (dispatcher.hasUpdates()) updatesQueued();
        if (children != null) {
            for (int i = 0, size = children.size(); i < size; i++) {
                Component child = children.get(i);
                child.markPendingSubtree();
            }
        }
    }

    /**
     * Moves attached mutations into the outgoing UI batch and discards work for removed components.
     * @param destination outgoing batch
     */
    void collectUpdates(DOMDispatcher destination) {
        if (isAttached()) destination.queue(dispatcher);
    }

    /**
     * Removes all child components from this component and queues a DOM update
     * operation to clear the corresponding child elements in the UI.
     * <p>
     * This method clears the internal children list and invokes the
     * {@link #queueForDispatch(DOMUpdateType)} method with the
     * {@link DOMUpdateType#CLEAR_CHILDREN} type to ensure the client-side DOM
     * reflects the removal.
     */
    public void clear() {
        if (this.children != null) {
            // Remove children from the end to avoid index shifting issues
            while (!children.isEmpty()) {
                remove(children.getLast());
            }
        }
        this.queueForDispatch(DOMUpdateType.CLEAR_CHILDREN);
        push();
    }

    /**
     * Disposes managed children before native text or HTML replaces their nodes.
     */
    protected void discardChildren() {
        if (children == null || children.isEmpty()) return;
        UI owner = getUI();
        owner.beginUpdate();
        try {
            while (!children.isEmpty()) remove(children.getLast());
        } finally {
            owner.endUpdate();
        }
    }

    /**
     * Reports whether this component is in the connected UI tree.
     * @return connected state
     */
    public boolean isAttached() {
        return parent != null ? parent.isAttached() : attached;
    }

    /**
     * Returns the {@link Style} object associated with this component.
     *
     * @return the style instance for dynamic CSS updates
     */
    public Style getStyle() {
        if (this.style == null)
            this.style = new Style(this, "#" + this.getComponentID());
        return style;
    }

    /**
     * Queues a DOM update operation for dispatch with the specified update type.
     * Constructs a {@link DOMUpdate} instance using the provided update type and
     * the component's unique identifier, then queues it for processing in the dispatcher.
     *
     * @param type the type of DOM update to queue (e.g., updates related to attributes, styles, or child elements)
     */
    public Component queueForDispatch(DOMUpdateType type) {
        this.dispatcher.queue(new DOMUpdate(type, getComponentID()));
        return this;
    }

    /**
     * Queues a DOM update operation for dispatch with the specified update type and parameters.
     * Constructs a {@link DOMUpdate} instance using the provided update type, component ID,
     * and parameters, then queues it for processing in the dispatcher.
     *
     * @param type       the type of DOM update to queue (e.g., updates related to attributes, styles, or child elements)
     * @param parameters a map of parameters specifying the details of the DOM update (e.g., attributes or values)
     */
    public Component queueForDispatch(DOMUpdateType type, Map<DOMUpdateParam, Object> parameters) {
        this.dispatcher.queue(new DOMUpdate(type, getComponentID()).params(parameters));
        return this;
    }

    /**
     * Queues a DOM update operation for dispatch with specific type, parameter, and value.
     * This method constructs a {@link DOMUpdate} instance using the provided update type,
     * component ID, parameter, and value, and queues it for processing in the dispatcher.
     *
     * @param type  the type of DOM update to queue (e.g., setting an attribute or appending a child)
     * @param param the parameter defining the aspect of the DOM to be updated (e.g., an attribute or property)
     * @param value the value associated with the parameter for the update (e.g., the new attribute value)
     */
    public Component queueForDispatch(DOMUpdateType type, DOMUpdateParam param, Object value) {
        this.dispatcher.queue(new DOMUpdate(type, getComponentID()).param(param, value));
        return this;
    }

    /**
     * Queues two non-null parameter values without allocating a temporary map.
     * @param type browser operation
     * @param firstKey first parameter key
     * @param firstValue first parameter value
     * @param secondKey second parameter key
     * @param secondValue second parameter value
     * @return this component
     */
    public Component queueForDispatch(DOMUpdateType type, DOMUpdateParam firstKey, Object firstValue, DOMUpdateParam secondKey, Object secondValue) {
        Objects.requireNonNull(firstValue, "firstValue");
        Objects.requireNonNull(secondValue, "secondValue");
        dispatcher.queue(new DOMUpdate(type, getComponentID()).param(firstKey, firstValue).param(secondKey, secondValue));
        return this;
    }

    /**
     * Queues three non-null parameter values without allocating a temporary map.
     * @param type browser operation
     * @param firstKey first parameter key
     * @param firstValue first parameter value
     * @param secondKey second parameter key
     * @param secondValue second parameter value
     * @param thirdKey third parameter key
     * @param thirdValue third parameter value
     * @return this component
     */
    public Component queueForDispatch(DOMUpdateType type, DOMUpdateParam firstKey, Object firstValue, DOMUpdateParam secondKey, Object secondValue, DOMUpdateParam thirdKey, Object thirdValue) {
        Objects.requireNonNull(firstValue, "firstValue");
        Objects.requireNonNull(secondValue, "secondValue");
        Objects.requireNonNull(thirdValue, "thirdValue");
        dispatcher.queue(new DOMUpdate(type, getComponentID()).param(firstKey, firstValue).param(secondKey, secondValue).param(thirdKey, thirdValue));
        return this;
    }

    /**
     * Executes a given JavaScript code string by converting it into a byte format
     * suitable for further processing.
     *
     * @param script the JavaScript code to be executed, represented as a string.
     *               This string will be converted to bytes in UTF-8 encoding.
     */
    public void executeJS(String script) {
        SessionContext context = SessionContext.get();
        getUI()
                .flushUpdates();
        context.send(TextPacketEncoder.encode(context.getChannel(), 4, script));
    }

    /**
     * Sets an attribute on the component, typically for updating the DOM representation.
     * Queues a DOM update operation for dispatch to the client.
     *
     * @param key   the name of the attribute to be updated
     * @param value the value to assign to the specified attribute
     */
    public void setAttribute(String key, String value) {
        this.queueForDispatch(DOMUpdateType.SET_ATTRIBUTE, DOMUpdateParam.KEY, key, DOMUpdateParam.VALUE, value);
        push();
    }

    /**
     * Adds a specific class name to the DOM representation of this component.
     * This method queues a DOM update operation to add the specified class name
     * and pushes the update to the client.
     *
     * @param name the class name to be added to the component
     */
    public void addClassName(String name) {
        this.queueForDispatch(DOMUpdateType.ADD_CLASS, DOMUpdateParam.CLASS_NAME, name);
        push();
    }

    /**
     * Removes a specific class name from the DOM representation of this component.
     * This method queues a DOM update operation to remove the specified class name
     * and pushes the update to the client.
     *
     * @param name the class name to be removed from the component
     */
    public void removeClassName(String name) {
        this.queueForDispatch(DOMUpdateType.REMOVE_CLASS, DOMUpdateParam.CLASS_NAME, name);
        push();
    }

    /**
     * Sets the inner HTML content of this component.
     * This method queues a DOM update operation with the specified HTML content
     * and pushes the update to the client.
     *
     * @param html the HTML content to set for this component
     */
    public void setHTML(String html) {
        discardChildren();
        this.queueForDispatch(DOMUpdateType.SET_HTML, DOMUpdateParam.HTML, html);
        push();
    }

    /**
     * Returns the unique identifier assigned to this component by its UI.
     *
     * @return the component ID
     */
    public int getComponentID() {
        if (this.componentID == -1)
            this.componentID = getUI()
                    .nextComponentID();
        return componentID;
    }

    /**
     * Retrieves the parent component of this component.
     *
     * @return the parent component if it exists, or null if this component does not have a parent
     */
    public Component getParent() {
        return parent;
    }

    /**
     * Resolves the owning UI from the current session on first access, allocating a component ID and style only when needed.
     * @return the owning session UI
     */
    public UI getUI() {
        if (this.ui == null)
            this.ui = SessionContext.get()
                    .getUI();
        return ui;
    }

    /**
     * Sets the enabled state of the component.
     * Queues a DOM update to toggle the "disabled" property based on the provided state.
     *
     * @param enabled {@code true} to enable the component, {@code false} to disable it
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.queueForDispatch(DOMUpdateType.SET_PROPERTY, DOMUpdateParam.PROPERTY, "disabled", DOMUpdateParam.VALUE, !enabled);
        this.push();
    }

    /**
     * Retrieves the HTML tag name associated with this component.
     *
     * @return the tag name used to render this component
     */
    public String getTag() {
        return tag;
    }

    /**
     * Determines whether this component is currently enabled.
     * The enabled state affects the component's interactivity and may
     * control whether it is usable or interactive within the UI.
     *
     * @return {@code true} if the component is enabled; {@code false} otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
}
