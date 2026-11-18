package geshra.net.web.ui.components.list;

import geshra.net.web.ui.components.Div;

/**
 * One live row wrapper with a logical key independent of its native component identifier.
 * This package-private type adds no object beside the wrapper already required for row layout.
 */
final class VirtualRow extends Div {
    private final String key; // Logical dataset identity, validated within the live window.

    /**
     * Creates an unattached row wrapper with its immutable logical identity.
     * @param key non-null logical key
     */
    VirtualRow(String key) { this.key = key; }

    /**
     * Returns the logical identity retained for bounded duplicate detection and removal.
     * @return row key
     */
    String key() { return key; }
}
