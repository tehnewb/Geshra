package geshra.net.web.ui;

import java.util.Map;

/**
 * One decoded browser mutation used by UI regressions. The parameter map owns copied wire strings;
 * no reference-counted Netty buffer or live component is retained after decoding.
 * @param type mutation code
 * @param target session component ID
 * @param parameters copied string parameters by wire key
 */
record ObservedMutation(int type, int target, Map<Integer, String> parameters) { }
