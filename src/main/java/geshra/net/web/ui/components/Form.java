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
        component.addValueChangeListener(e -> values.put(name, e.getNewValue()));
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
        checkbox.addValueChangeListener(e -> values.put(name, e.getNewValue()));

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
                .label(text);
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
     * Retrieves an unmodifiable view of the current form values.
     *
     * @return Map of field names to their current values.
     */
    public Map<String, Object> getValues() {
        return Collections.unmodifiableMap(values);
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
     * Forms do not require cleanup beyond base Component destruction.
     */
    @Override
    protected void destroy() {
        // No cleanup required
    }
}
