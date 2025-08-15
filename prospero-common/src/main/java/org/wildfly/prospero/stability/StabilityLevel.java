package org.wildfly.prospero.stability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark classes, methods, or fields with a required stability level.
 * <p>
 * This annotation is used throughout the Prospero system to control feature availability
 * based on the current distribution's stability level. Features annotated with a specific
 * stability level will only be available when the current distribution stability permits
 * access to that level.
 * </p>
 *
 * <h3>Usage Patterns</h3>
 *
 * <h4>Command Classes</h4>
 * <p>Commands annotated with {@code @StabilityLevel} will be filtered out if the current
 * distribution stability doesn't permit the specified level:</p>
 * <pre>{@code
 * @StabilityLevel(level = Stability.Community)
 * @CommandLine.Command(name = "experimental-feature")
 * public class ExperimentalCommand extends AbstractCommand {
 *     // This command only appears at Community level or lower
 * }
 * }</pre>
 *
 * <h4>Command Options</h4>
 * <p>Individual command-line options can be restricted to specific stability levels:</p>
 * <pre>{@code
 * public class MyCommand extends AbstractCommand {
 *     @CommandLine.Option(names = "--experimental-option")
 *     @StabilityLevel(level = Stability.Experimental)
 *     boolean experimentalFeature;  // Only available at Experimental level
 * }
 * }</pre>
 *
 * <h4>API Methods</h4>
 * <p>API methods can be protected to prevent usage at inappropriate stability levels:</p>
 * <pre>{@code
 * public class InstallationManager {
 *     @StabilityLevel(level = Stability.Preview)
 *     public void experimentalOperation() {
 *         // This method is checked at runtime via proxy
 *     }
 * }
 * }</pre>
 *
 * <h3>Runtime Behavior</h3>
 * <ul>
 * <li><strong>CLI Commands</strong>: Filtered by {@link org.wildfly.prospero.cli.StabilityAwareCommandBuilder}</li>
 * <li><strong>CLI Options</strong>: Hidden from help and rejected during parsing</li>
 * <li><strong>API Methods</strong>: Protected by runtime checks in {@link StabilityUtils}</li>
 * <li><strong>Test Methods</strong>: Skipped by {@link org.wildfly.utils.StabilityLevelAwareRunner}</li>
 * </ul>
 *
 * <h3>Default Behavior</h3>
 * <p>
 * If no {@code @StabilityLevel} annotation is present, the feature is considered to be
 * at {@link Stability#Default} level and will be available in all distributions.
 * </p>
 *
 * @since 1.4.0
 * @see Stability
 * @see StabilityUtils#ensureAllowed(Stability, String, String)
 * @see org.wildfly.prospero.cli.StabilityAwareCommandBuilder
 */
@Retention(value = RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface StabilityLevel {
    /**
     * The minimum stability level required to access the annotated feature.
     * <p>
     * The feature will only be available when the current distribution's stability
     * level {@linkplain Stability#permits(Stability) permits} this level.
     * </p>
     * <p>
     * If not specified, defaults to {@link Stability#Default}, making the feature
     * available in all distributions.
     * </p>
     *
     * @return the required stability level
     */
    Stability level() default Stability.Default;
}
