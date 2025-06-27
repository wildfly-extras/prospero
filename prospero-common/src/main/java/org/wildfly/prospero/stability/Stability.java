package org.wildfly.prospero.stability;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Represents the stability levels available in the Prospero ion system.
 * <p>
 * Stability levels control which features, commands, and APIs are available to users.
 * Higher stability levels (closer to Default) provide more stable, production-ready features,
 * while lower stability levels (closer to Experimental) provide access to newer, less stable features.
 * </p>
 *
 * <h3>Stability Level Hierarchy</h3>
 * <p>
 * The stability levels form a hierarchy where each level "permits" all levels to its right:
 * </p>
 * <pre>
 * Experimental → Preview → Community → Default
 * (least stable)                    (most stable)
 * </pre>
 *
 * <h3>Permission Model</h3>
 * <ul>
 * <li><strong>Experimental</strong>: Permits all features (Experimental, Preview, Community, Default)</li>
 * <li><strong>Preview</strong>: Permits Preview, Community, and Default features</li>
 * <li><strong>Community</strong>: Permits Community and Default features</li>
 * <li><strong>Default</strong>: Permits only Default (stable) features</li>
 * </ul>
 *
 * <h3>Usage Examples</h3>
 * <pre>{@code
 * // Check if current stability permits a feature
 * if (currentStability.permits(Stability.Community)) {
 *     // Enable community-level feature
 * }
 *
 * // Parse stability from string
 * Stability level = Stability.from("preview");
 *
 * // Check hierarchy
 * assert Stability.Experimental.permits(Stability.Default); // true
 * assert !Stability.Default.permits(Stability.Experimental); // false
 * }</pre>
 *
 * @since 1.4.0
 * @see StabilityLevel
 * @see org.wildfly.prospero.DistributionInfo#getStability()
 */
public enum Stability {
    // NOTE: the order of the enums defines which scope permits other
    // keep the left->right order from the least restrictive to the most restrictive

    /**
     * Experimental stability level - provides access to all features including unstable,
     * experimental APIs and commands. Use with caution in production environments.
     */
    Experimental,

    /**
     * Preview stability level - provides access to preview features that are more stable
     * than experimental but not yet considered production-ready.
     */
    Preview,

    /**
     * Community stability level - provides access to community-supported features
     * and the ability to override stability levels via command-line arguments.
     */
    Community,

    /**
     * Default stability level - provides access only to stable, production-ready features.
     * This is the most restrictive level and the default for production ions.
     */
    Default;

    /**
     * Parses a stability level from its string representation.
     * <p>
     * The parsing is case-insensitive, so "EXPERIMENTAL", "experimental", and "Experimental"
     * all resolve to {@link #Experimental}.
     * </p>
     *
     * @param stability the string representation of the stability level
     * @return the corresponding Stability enum value
     * @throws IllegalArgumentException if the stability string is not recognized
     *
     * @see #allowedValues()
     */
    public static Stability from(String stability) {
        return Arrays.stream(values())
                .filter(s->s.toString().equalsIgnoreCase(stability))
                .findFirst()
                .orElseThrow(()->new IllegalArgumentException(
                        "Unknown stability level %s. Allowed values are %s".formatted(
                                stability, allowedValues()
                        )));
    }

    /**
     * Returns a comma-separated string of all allowed stability level values.
     * <p>
     * This is primarily used for error messages and help text.
     * </p>
     *
     * @return a string like "experimental, preview, community, default"
     */
    private static String allowedValues() {
        return Arrays.stream(values()).map(Stability::toString).map(String::toLowerCase).collect(Collectors.joining(", "));
    }

    /**
     * Checks if this stability level permits access to features at the specified level.
     * <p>
     * The permission model follows a hierarchy where lower stability levels (more experimental)
     * permit access to higher stability levels (more stable), but not vice versa.
     * </p>
     *
     * <h4>Examples:</h4>
     * <ul>
     * <li>{@code Experimental.permits(Default)} → {@code true}</li>
     * <li>{@code Community.permits(Preview)} → {@code false}</li>
     * <li>{@code Default.permits(Default)} → {@code true}</li>
     * </ul>
     *
     * @param other the stability level to check access for
     * @return {@code true} if this level permits access to the other level, {@code false} otherwise
     *
     * @see #ordinal()
     */
    public boolean permits(Stability other) {
        return this.ordinal() <= other.ordinal();
    }
}
