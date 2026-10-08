package geshra.net.web.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * The {@code Key} enum defines a comprehensive set of string constants
 * corresponding to values returned by JavaScript's {@code event.key} property
 * in {@code KeyboardEvent} handlers.
 *
 * <p>This enum can be used to map keypresses from the browser to named constants
 * in Java, making it easier to interpret and respond to keyboard input consistently
 * across your application.
 *
 * <p>Each constant mapped directly to the {@code String} representation returned by
 * the browser when a specific key is pressed. For example, pressing the "a" key
 * returns {@code "a"}, and pressing the left arrow key returns {@code "ArrowLeft"}.
 *
 * <p>Use the {@link #fromName(String)} method to convert a raw key string from
 * JavaScript into a corresponding enum constant, and {@link #getKey()} to get
 * the string form of an enum constant.
 *
 * @author Albert Beaupre
 * @version 1.0
 * @since April 19, 2025
 */
public enum Key {

    // --- Alphanumeric keys ---
    /**
     * Browser key value for a.
     */
    a("a"),
    /**
     * Browser key value for b.
     */
    b("b"),
    /**
     * Browser key value for c.
     */
    c("c"),
    /**
     * Browser key value for d.
     */
    d("d"),
    /**
     * Browser key value for e.
     */
    e("e"),
    /**
     * Browser key value for f.
     */
    f("f"),
    /**
     * Browser key value for g.
     */
    g("g"),
    /**
     * Browser key value for h.
     */
    h("h"),
    /**
     * Browser key value for i.
     */
    i("i"),
    /**
     * Browser key value for j.
     */
    j("j"),
    /**
     * Browser key value for k.
     */
    k("k"),
    /**
     * Browser key value for l.
     */
    l("l"),
    /**
     * Browser key value for m.
     */
    m("m"),
    /**
     * Browser key value for n.
     */
    n("n"),
    /**
     * Browser key value for o.
     */
    o("o"),
    /**
     * Browser key value for p.
     */
    p("p"),
    /**
     * Browser key value for q.
     */
    q("q"),
    /**
     * Browser key value for r.
     */
    r("r"),
    /**
     * Browser key value for s.
     */
    s("s"),
    /**
     * Browser key value for t.
     */
    t("t"),
    /**
     * Browser key value for u.
     */
    u("u"),
    /**
     * Browser key value for v.
     */
    v("v"),
    /**
     * Browser key value for w.
     */
    w("w"),
    /**
     * Browser key value for x.
     */
    x("x"),
    /**
     * Browser key value for y.
     */
    y("y"),
    /**
     * Browser key value for z.
     */
    z("z"),
    /**
     * Browser key value for A.
     */
    A("A"),
    /**
     * Browser key value for B.
     */
    B("B"),
    /**
     * Browser key value for C.
     */
    C("C"),
    /**
     * Browser key value for D.
     */
    D("D"),
    /**
     * Browser key value for E.
     */
    E("E"),
    /**
     * Browser key value for F.
     */
    F("F"),
    /**
     * Browser key value for G.
     */
    G("G"),
    /**
     * Browser key value for H.
     */
    H("H"),
    /**
     * Browser key value for I.
     */
    I("I"),
    /**
     * Browser key value for J.
     */
    J("J"),
    /**
     * Browser key value for K.
     */
    K("K"),
    /**
     * Browser key value for L.
     */
    L("L"),
    /**
     * Browser key value for M.
     */
    M("M"),
    /**
     * Browser key value for N.
     */
    N("N"),
    /**
     * Browser key value for O.
     */
    O("O"),
    /**
     * Browser key value for P.
     */
    P("P"),
    /**
     * Browser key value for Q.
     */
    Q("Q"),
    /**
     * Browser key value for R.
     */
    R("R"),
    /**
     * Browser key value for S.
     */
    S("S"),
    /**
     * Browser key value for T.
     */
    T("T"),
    /**
     * Browser key value for U.
     */
    U("U"),
    /**
     * Browser key value for V.
     */
    V("V"),
    /**
     * Browser key value for W.
     */
    W("W"),
    /**
     * Browser key value for X.
     */
    X("X"),
    /**
     * Browser key value for Y.
     */
    Y("Y"),
    /**
     * Browser key value for Z.
     */
    Z("Z"),
    /**
     * Browser key value for ZERO.
     */
    ZERO("0"),
    /**
     * Browser key value for ONE.
     */
    ONE("1"),
    /**
     * Browser key value for TWO.
     */
    TWO("2"),
    /**
     * Browser key value for THREE.
     */
    THREE("3"),
    /**
     * Browser key value for FOUR.
     */
    FOUR("4"),
    /**
     * Browser key value for FIVE.
     */
    FIVE("5"),
    /**
     * Browser key value for SIX.
     */
    SIX("6"),
    /**
     * Browser key value for SEVEN.
     */
    SEVEN("7"),
    /**
     * Browser key value for EIGHT.
     */
    EIGHT("8"),
    /**
     * Browser key value for NINE.
     */
    NINE("9"),

    // --- Whitespace and control ---
    /**
     * Browser key value for SPACE.
     */
    SPACE(" "),
    /**
     * Browser key value for TAB.
     */
    TAB("Tab"),
    /**
     * Browser key value for ENTER.
     */
    ENTER("Enter"),
    /**
     * Browser key value for BACKSPACE.
     */
    BACKSPACE("Backspace"),
    /**
     * Browser key value for DELETE.
     */
    DELETE("Delete"),
    /**
     * Browser key value for INSERT.
     */
    INSERT("Insert"),
    /**
     * Browser key value for HOME.
     */
    HOME("Home"),
    /**
     * Browser key value for END.
     */
    END("End"),
    /**
     * Browser key value for PAGE_UP.
     */
    PAGE_UP("PageUp"),
    /**
     * Browser key value for PAGE_DOWN.
     */
    PAGE_DOWN("PageDown"),

    // --- Modifier keys ---
    /**
     * Browser key value for SHIFT.
     */
    SHIFT("Shift"),
    /**
     * Browser key value for CONTROL.
     */
    CONTROL("Control"),
    /**
     * Browser key value for ALT.
     */
    ALT("Alt"),
    /**
     * Browser key value for META.
     */
    META("Meta"),
    /**
     * Browser key value for CAPS_LOCK.
     */
    CAPS_LOCK("CapsLock"),
    /**
     * Browser key value for ESCAPE.
     */
    ESCAPE("Escape"),

    // --- Arrow keys ---
    /**
     * Browser key value for ARROW_LEFT.
     */
    ARROW_LEFT("ArrowLeft"),
    /**
     * Browser key value for ARROW_UP.
     */
    ARROW_UP("ArrowUp"),
    /**
     * Browser key value for ARROW_RIGHT.
     */
    ARROW_RIGHT("ArrowRight"),
    /**
     * Browser key value for ARROW_DOWN.
     */
    ARROW_DOWN("ArrowDown"),

    // --- Function keys ---
    /**
     * Browser key value for F1.
     */
    F1("F1"),
    /**
     * Browser key value for F2.
     */
    F2("F2"),
    /**
     * Browser key value for F3.
     */
    F3("F3"),
    /**
     * Browser key value for F4.
     */
    F4("F4"),
    /**
     * Browser key value for F5.
     */
    F5("F5"),
    /**
     * Browser key value for F6.
     */
    F6("F6"),
    /**
     * Browser key value for F7.
     */
    F7("F7"),
    /**
     * Browser key value for F8.
     */
    F8("F8"),
    /**
     * Browser key value for F9.
     */
    F9("F9"),
    /**
     * Browser key value for F10.
     */
    F10("F10"),
    /**
     * Browser key value for F11.
     */
    F11("F11"),
    /**
     * Browser key value for F12.
     */
    F12("F12"),
    /**
     * Browser key value for PRINT_SCREEN.
     */
    PRINT_SCREEN("PrintScreen"),
    /**
     * Browser key value for SCROLL_LOCK.
     */
    SCROLL_LOCK("ScrollLock"),
    /**
     * Browser key value for PAUSE.
     */
    PAUSE("Pause"),

    // --- Punctuation and symbols ---
    /**
     * Browser key value for EXCLAMATION.
     */
    EXCLAMATION("!"),
    /**
     * Browser key value for AT.
     */
    AT("@"),
    /**
     * Browser key value for HASH.
     */
    HASH("#"),
    /**
     * Browser key value for DOLLAR.
     */
    DOLLAR("$"),
    /**
     * Browser key value for PERCENT.
     */
    PERCENT("%"),
    /**
     * Browser key value for CARET.
     */
    CARET("^"),
    /**
     * Browser key value for AMPERSAND.
     */
    AMPERSAND("&"),
    /**
     * Browser key value for ASTERISK.
     */
    ASTERISK("*"),
    /**
     * Browser key value for LEFT_PAREN.
     */
    LEFT_PAREN("("),
    /**
     * Browser key value for RIGHT_PAREN.
     */
    RIGHT_PAREN(")"),
    /**
     * Browser key value for DASH.
     */
    DASH("-"),
    /**
     * Browser key value for UNDERSCORE.
     */
    UNDERSCORE("_"),
    /**
     * Browser key value for EQUALS.
     */
    EQUALS("="),
    /**
     * Browser key value for PLUS.
     */
    PLUS("+"),
    /**
     * Browser key value for LEFT_BRACKET.
     */
    LEFT_BRACKET("["),
    /**
     * Browser key value for RIGHT_BRACKET.
     */
    RIGHT_BRACKET("]"),
    /**
     * Browser key value for LEFT_BRACE.
     */
    LEFT_BRACE("{"),
    /**
     * Browser key value for RIGHT_BRACE.
     */
    RIGHT_BRACE("}"),
    /**
     * Browser key value for SEMICOLON.
     */
    SEMICOLON(";"),
    /**
     * Browser key value for COLON.
     */
    COLON(":"),
    /**
     * Browser key value for SINGLE_QUOTE.
     */
    SINGLE_QUOTE("'"),
    /**
     * Browser key value for DOUBLE_QUOTE.
     */
    DOUBLE_QUOTE("\""),
    /**
     * Browser key value for COMMA.
     */
    COMMA(","),
    /**
     * Browser key value for PERIOD.
     */
    PERIOD("."),
    /**
     * Browser key value for LESS_THAN.
     */
    LESS_THAN("<"),
    /**
     * Browser key value for GREATER_THAN.
     */
    GREATER_THAN(">"),
    /**
     * Browser key value for SLASH.
     */
    SLASH("/"),
    /**
     * Browser key value for QUESTION.
     */
    QUESTION("?"),
    /**
     * Browser key value for BACKSLASH.
     */
    BACKSLASH("\\"),
    /**
     * Browser key value for PIPE.
     */
    PIPE("|"),
    /**
     * Browser key value for BACKTICK.
     */
    BACKTICK("`"),
    /**
     * Browser key value for TILDE.
     */
    TILDE("~"),

    // --- Numpad keys ---
    /**
     * Browser key value for NUMPAD_0.
     */
    NUMPAD_0("0"),
    /**
     * Browser key value for NUMPAD_1.
     */
    NUMPAD_1("1"),
    /**
     * Browser key value for NUMPAD_2.
     */
    NUMPAD_2("2"),
    /**
     * Browser key value for NUMPAD_3.
     */
    NUMPAD_3("3"),
    /**
     * Browser key value for NUMPAD_4.
     */
    NUMPAD_4("4"),
    /**
     * Browser key value for NUMPAD_5.
     */
    NUMPAD_5("5"),
    /**
     * Browser key value for NUMPAD_6.
     */
    NUMPAD_6("6"),
    /**
     * Browser key value for NUMPAD_7.
     */
    NUMPAD_7("7"),
    /**
     * Browser key value for NUMPAD_8.
     */
    NUMPAD_8("8"),
    /**
     * Browser key value for NUMPAD_9.
     */
    NUMPAD_9("9"),
    /**
     * Browser key value for NUMPAD_DECIMAL.
     */
    NUMPAD_DECIMAL("."),
    /**
     * Browser key value for NUMPAD_ADD.
     */
    NUMPAD_ADD("+"),
    /**
     * Browser key value for NUMPAD_SUBTRACT.
     */
    NUMPAD_SUBTRACT("-"),
    /**
     * Browser key value for NUMPAD_MULTIPLY.
     */
    NUMPAD_MULTIPLY("*"),
    /**
     * Browser key value for NUMPAD_DIVIDE.
     */
    NUMPAD_DIVIDE("/"),
    /**
     * Browser key value for NUMPAD_ENTER.
     */
    NUMPAD_ENTER("Enter"),
    /**
     * Browser key value for NUM_LOCK.
     */
    NUM_LOCK("NumLock"),

    // --- Other special keys ---
    /**
     * Browser key value for CONTEXT_MENU.
     */
    CONTEXT_MENU("ContextMenu"),
    /**
     * Browser key value for UNIDENTIFIED.
     */
    UNIDENTIFIED("Unidentified");

    /**
     * Immutable-after-initialization browser names, preserving the first enum constant for aliases.
     */
    private static final Map<String, Key> BY_NAME = buildNameIndex();
    private final String key; // The string representation of the JavaScript key, as returned by event.key.

    /**
     * Constructs a new {@code Key} enum with the specified string representation.
     *
     * @param key the exact string value returned by {@code event.key} in JavaScript
     */
    Key(String key) {
        this.key = key;
    }

    /**
     * Returns the string value associated with this key, which corresponds to
     * what JavaScript would return from {@code event.key}.
     *
     * @return the string representation of the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Resolves a {@code Key} from a raw string key value typically
     * returned by {@code KeyboardEvent.key} in the browser.
     *
     * <p>This is useful when mapping JavaScript keyboard input to
     * corresponding enum values in server-side or Java-based code.
     *
     * @param key the raw {@code event.key} string
     * @return the matching {@code Key} constant, or {@code null} if none found
     */
    public static Key fromName(String key) {
        /*
         * A single name lookup avoids cloning and scanning the complete enum for every key event.
         */
        return BY_NAME.get(key);
    }

    /**
     * Builds the name index once, retaining the original first-match behavior for numpad aliases.
     * @return completed name index
     */
    private static Map<String, Key> buildNameIndex() {
        /*
         * Duplicate browser names intentionally map to the first declared constant, matching the
         * old linear lookup while moving all allocation to class initialization.
         */
        Map<String, Key> index = new HashMap<>();
        for (Key key : values()) index.putIfAbsent(key.key, key);
        return index;
    }
}
