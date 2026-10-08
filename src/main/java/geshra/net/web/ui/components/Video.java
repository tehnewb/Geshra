package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native video player. Playback, captions, loading, permissions, and media events belong to the
 * browser. Autoplay restrictions also apply to server-requested play calls.
 */
public class Video extends Component {
    /**
     * Creates a native player with controls.
     * @param source media URL
     */
    public Video(String source) { super("video"); setProperty("controls", true); attribute("src", source); }

    /**
     * Requests native playback.
     * @return this player
     */
    public Video play() { callMethod("play"); return this; }

    /**
     * Requests native pause.
     * @return this player
     */
    public Video pause() { callMethod("pause"); return this; }

    /**
     * Seeks using the native currentTime property.
     * @param seconds requested time
     * @return this player
     */
    public Video seek(double seconds) { setProperty("currentTime", seconds); return this; }

    /**
     * Sets the native muted property.
     * @param muted silence audio
     * @return this player
     */
    public Video setMuted(boolean muted) { setProperty("muted", muted); return this; }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }

}
