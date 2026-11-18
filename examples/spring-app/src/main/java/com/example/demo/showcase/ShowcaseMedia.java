package com.example.demo.showcase;

import geshra.net.web.ui.components.*;

/**
 * Local-only image, audio, video, canvas and SVG examples. Media files are bundled with the consumer,
 * so no third-party network service is required. Native browser controls own playback permissions;
 * server actions use the real component APIs and expose playback events in visible feedback.
 */
public final class ShowcaseMedia {
    /**
     * Prevents construction of this stateless category factory.
     */
    private ShowcaseMedia() { }

    /**
     * Creates local media and every public SVG primitive.
     * @return new graphics/media category
     */
    public static Section create() {
        /*
         * Use small deterministic fixtures, not external streams or autoplay permissions.
         */
        Image image = new Image("/showcase-assets/sample.svg", "Blue Geshra sample illustration");
        image.attribute("width", "240");
        image.attribute("height", "100");
        Figure figure = new Figure(image, new Paragraph("Local SVG illustration in a Figure"));
        Audio audio = new Audio("/showcase-assets/sample.wav");
        audio.ariaLabel("Sample audio");
        Video video = new Video("/showcase-assets/sample.webm");
        video.attribute("width", "240");
        video.attribute("height", "135");
        video.ariaLabel("Sample video");
        Paragraph playback = new Paragraph("Playback idle");
        audio.listen("play", event -> playback.setText("Audio playing"));
        audio.listen("pause", event -> playback.setText("Audio paused"));
        video.listen("play", event -> playback.setText("Video playing"));
        video.listen("pause", event -> playback.setText("Video paused"));
        Canvas canvas = new Canvas(240, 100);
        canvas.ariaLabel("Canvas drawing");
        canvas.setDrawingProperty("fillStyle", "#2563eb");
        canvas.draw("fillRect", 10, 10, 90, 60);
        Button redraw = ShowcaseSamples.action("Draw canvas circle", () -> { canvas.setDrawingProperty("fillStyle", "#16a34a"); canvas.draw("beginPath"); canvas.draw("arc", 170, 50, 30, 0, Math.PI * 2); canvas.draw("fill"); });
        SvgRectangle rectangle = new SvgRectangle();
        rectangle.attribute("x", "5")
                .attribute("y", "5")
                .attribute("width", "50")
                .attribute("height", "40")
                .attribute("fill", "#2563eb");
        SvgCircle circle = new SvgCircle();
        circle.attribute("cx", "90")
                .attribute("cy", "25")
                .attribute("r", "20")
                .attribute("fill", "#16a34a");
        SvgLine line = new SvgLine();
        line.attribute("x1", "120")
                .attribute("y1", "5")
                .attribute("x2", "160")
                .attribute("y2", "45")
                .attribute("stroke", "#9333ea")
                .attribute("stroke-width", "4");
        SvgPolyline polyline = new SvgPolyline();
        polyline.attribute("points", "175,40 190,5 210,40")
                .attribute("fill", "none")
                .attribute("stroke", "#ea580c")
                .attribute("stroke-width", "4");
        SvgPolygon polygon = new SvgPolygon();
        polygon.attribute("points", "225,40 240,5 260,40")
                .attribute("fill", "#db2777");
        SvgPath path = new SvgPath();
        path.attribute("d", "M 275 40 Q 295 0 315 40")
                .attribute("fill", "none")
                .attribute("stroke", "#0f766e")
                .attribute("stroke-width", "4");
        SvgText text = new SvgText();
        text.setProperty("textContent", "SVG text");
        text.attribute("x", "5")
                .attribute("y", "80");
        Svg svg = new Svg(new SvgGroup(rectangle, circle, line, polyline, polygon, path, text));
        svg.attribute("viewBox", "0 0 330 100")
                .attribute("width", "330")
                .attribute("height", "100")
                .attribute("role", "img");
        svg.ariaLabel("SVG primitive shapes");
        Script script = new Script();
        Link resource = new Link("/showcase-assets/showcase.css", "stylesheet");
        return ShowcaseSamples.section("Graphics and media",
                ShowcaseSamples.sample("Image,Figure", "Image description accessibility is under test. This fixture is local and reloadable.", figure),
                ShowcaseSamples.sample("Audio,Video", "Use native playback controls. Pause works through Java callbacks; play may require a direct browser user gesture.", audio, video, new ButtonBar(ShowcaseSamples.action("Pause audio", () -> audio.pause()), ShowcaseSamples.action("Pause video", () -> video.pause())), playback),
                ShowcaseSamples.sample("Canvas", "Blue rectangle is drawn through Canvas 2D commands; add a green circle or reload to reset.", canvas, redraw),
                ShowcaseSamples.sample("Svg,SvgGroup,SvgRectangle,SvgCircle,SvgLine,SvgPolyline,SvgPolygon,SvgPath,SvgText", "Every SVG primitive in a native SVG namespace.", svg),
                ShowcaseSamples.sample("Script,Link", "Resource elements have no visible widget. Link loads this showcase stylesheet; Script is deliberately empty and executes no arbitrary code.", script, resource));
    }
}
