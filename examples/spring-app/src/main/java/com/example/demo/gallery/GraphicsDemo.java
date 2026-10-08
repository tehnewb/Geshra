package com.example.demo.gallery;

import geshra.net.web.ui.components.*;

/**
 * Native canvas commands and SVG attributes expressed through Java components.
 * Each call creates a section owned by the current browser session.
 */
public final class GraphicsDemo {
    /**
     * Prevents construction of this stateless example factory.
     */
    private GraphicsDemo() {
    }

    /**
     * Creates the interactive example and its explanatory content.
     *
     * @return a new section ready to add to a route
     */
    public static Section create() {
        /*
         * Queue canvas drawing commands and build SVG nodes using their native browser APIs.
         */
        Canvas canvas = new Canvas(160, 80);
        canvas.ariaLabel("Canvas example");
        // These names and arguments match the browser CanvasRenderingContext2D API.
        canvas.setDrawingProperty("fillStyle", "#1565c0");
        canvas.draw("fillRect", 10, 10, 80, 40);
        // SVG attributes keep their native spelling, including the case-sensitive viewBox.
        SvgCircle circle = new SvgCircle();
        circle.attribute("cx", "40");
        circle.attribute("cy", "40");
        circle.attribute("r", "25");
        circle.attribute("fill", "#1565c0");
        Svg svg = new Svg(circle);
        svg.attribute("viewBox", "0 0 80 80");
        svg.attribute("width", "80");
        svg.attribute("height", "80");
        svg.ariaLabel("SVG example");

        return new Section(
                new H2("Native graphics"),
                new Paragraph("The rectangle uses the canvas 2D context; the circle is an SVG element with native attributes."),
                new HorizontalLayout(canvas, svg)
        );
    }
}
