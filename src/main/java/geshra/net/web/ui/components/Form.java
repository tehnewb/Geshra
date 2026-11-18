package geshra.net.web.ui.components;

import geshra.event.EventHandler;
import geshra.event.EventTypes;
import geshra.net.web.ui.Component;
import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;
import geshra.net.web.ui.Designer;
import geshra.net.web.ui.css.AlignItems;
import geshra.net.web.ui.css.AlignSelf;
import geshra.net.web.ui.css.Display;
import geshra.net.web.ui.css.FlexDirection;
import geshra.net.web.ui.event.SubmitEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Iterator;
import java.lang.ref.WeakReference;

/**
 * Form
 * <p>
 * Represents an HTML {@code <form>} element within the UI framework.
 * Manages named input components, collects their values, and dispatches
 * a {@link SubmitEvent} when the form is submitted.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since May 17th, 2025
 */
public class Form extends Component {

    private final Map<String, Object> values = new LinkedHashMap<>(); // Stores the current values for each named form field. Keys are the "name" attribute of child components; values are their latest values.
    private final Map<String, WeakReference<Component>> fields = new LinkedHashMap<>(); // Non-owning bindings; the component tree owns field lifetimes and the latest binding wins for duplicate names.

    /**
     * Creates a new Form component rendered as a {@code <form>} element.
     */
    public Form() {
        super("form");
    }

    /**
     * Adds arbitrary child components into this form.
     * Overrides Component.add to return a Form for fluent chaining.
     *
     * @param children Components to append as children of the form.
     * @return this Form instance
     */
    @Override
    public Form add(Component... children) {
        super.add(children);
        return this;
    }

    /**
     * Adds an input-like component (text field, select, etc.) and binds it to a field name.
     * Attaches a "name" attribute and a listener to update internal values map on changes.
     *
     * @param name      The field name under which the component's value will be stored.
     * @param component The input component to add.
     * @return this Form instance
     */
    public Form addFormItem(String name, Component component) {
        // Ensure the component has the proper name attribute in the rendered DOM
        component.queueForDispatch(DOMUpdateType.SET_ATTRIBUTE, DOMUpdateParam.KEY, "name", DOMUpdateParam.VALUE, name);
        // Listen for value-change events to keep values map up to date
        bindField(name, component);
        this.add(component);
        return this;
    }

    /**
     * Adds a submit-capable component (e.g., a button) to this form.
     * Sets its "type" attribute to "submit" so that clicking it triggers form submission.
     *
     * @param component The component to act as the submit control.
     * @return this Form instance
     */
    public Form addSubmitItem(Component component) {
        component.queueForDispatch(DOMUpdateType.SET_ATTRIBUTE, DOMUpdateParam.KEY, "type", DOMUpdateParam.VALUE, "submit");
        this.add(component);
        return this;
    }

    /**
     * Convenience method to add a checkbox with a label.
     * Wraps the checkbox and label in a flex container for layout.
     * Binds the checkbox's value to the given field name.
     *
     * @param name The name under which the checkbox value is stored.
     * @param text The label text displayed beside the checkbox.
     * @return this Form instance
     */
    public Form addCheckbox(String name, String text) {
        Checkbox checkbox = new Checkbox();
        // Set name attribute on the checkbox element
        checkbox.queueForDispatch(DOMUpdateType.SET_ATTRIBUTE, DOMUpdateParam.KEY, "name", DOMUpdateParam.VALUE, name);
        // Update values map when checkbox state changes
        bindField(name, checkbox);

        // Use Designer API to create a flex container with checkbox + label
        Designer.begin(this)
                .div()
                .asParent()
                .alignSelf(AlignSelf.START)
                .display(Display.FLEX)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.BASELINE)
                .gap("0.5em")
                .component(checkbox)
                .component(new Label(text, checkbox));
        return this;
    }

    /**
     * Registers a listener for the form's submit event.
     * The provided listener will be invoked when the form is submitted.
     *
     * @param submitListener Listener to handle {@link SubmitEvent}.
     * @return this Form instance
     */
    public Form addSubmitListener(EventHandler<SubmitEvent> submitListener) {
        this.registerEventListener(EventTypes.SUBMIT, submitListener, "submit");
        return this;
    }

    /**
     * Registers a submit handler with explicit priority.
     * @param priority larger values execute first
     * @param submitListener form-submit handler
     * @return this form
     */
    public Form addSubmitListener(int priority, EventHandler<SubmitEvent> submitListener) {
        this.registerEventListener(EventTypes.SUBMIT, priority, submitListener, "submit");
        return this;
    }

    /**
     * Retrieves an unmodifiable view of the current form values. Typed controls are read on each
     * call so untouched initial values, silent assignments and browser normalization are included.
     * Generic components retain their latest value-change callback payload. The returned map is
     * backed by this form and is valid only on its owning session dispatch thread.
     *
     * @return Map of field names to their current values.
     */
    public Map<String, Object> getValues() {
        Iterator<Map.Entry<String, WeakReference<Component>>> iterator = fields.entrySet()
                .iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, WeakReference<Component>> entry = iterator.next();
            Component component = entry.getValue()
                    .get();
            if (component == null) {
                values.remove(entry.getKey());
                iterator.remove();
            } else if (!ownsField(component)) values.remove(entry.getKey());
            else if (component instanceof ValueComponent<?> valueComponent) values.put(entry.getKey(), valueComponent.getValue());
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * Replaces a named binding without retaining removed controls or accepting obsolete callbacks.
     * @param name submitted field name; the newest binding replaces an earlier binding with this name
     * @param component control owned by this form's component tree
     */
    private void bindField(String name, Component component) {
        WeakReference<Component> binding = new WeakReference<>(component);
        fields.put(name, binding);
        values.remove(name);
        if (component instanceof ValueComponent<?> valueComponent) values.put(name, valueComponent.getValue());
        component.addValueChangeListener(event -> {
            if (fields.get(name) == binding && ownsField(component)) values.put(name, event.getNewValue());
        });
    }

    /**
     * Checks parent ownership without allocating IDs or retaining obsolete descendants.
     * @param component candidate control; null means it has been collected
     * @return whether this form still contains the candidate
     */
    private boolean ownsField(Component component) {
        while (component != null) {
            if (component == this) return true;
            component = component.getParent();
        }
        return false;
    }

    @Override
    public void clear() {
        super.clear();
        fields.clear();
        values.clear();
    }

    /**
     * Lifecycle hook invoked when the component is created.
     * Forms do not require additional setup beyond base Component initialization.
     */
    @Override
    protected void create() {
        // No additional setup required
    }

    /**
     * Lifecycle hook invoked when the component is destroyed.
     * Releases non-owning field bindings and cached values when this form is disposed.
     */
    @Override
    protected void destroy() {
        fields.clear();
        values.clear();
    }
}
