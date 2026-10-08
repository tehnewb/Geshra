package geshra.net.web.ui.components;



/**
 * A collection of native Details elements. Add Details children and give them a common group name
 * when exclusive opening is desired; native disclosures handle keyboard access and retained state.
 */
public class Accordion extends Div {
    /**
     * Creates an accordion.
     * @param sections native disclosure sections
     */
    public Accordion(Details... sections) { attribute("class", "geshra-accordion"); add(sections); }


}
