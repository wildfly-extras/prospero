/**
 * Core stability level system for the Prospero ion management platform.
 * <p>
 * This package provides the fundamental infrastructure for managing feature availability
 * based on stability levels. The stability system allows ions to control which
 * features, commands, and APIs are available to users based on their stability requirements.
 * </p>
 *
 * <h2>Overview</h2>
 * <p>
 * The stability level system is built around the concept of hierarchical stability levels
 * that control feature access. Features are annotated with required stability levels, and
 * the system automatically filters available functionality based on the current ion's
 * stability configuration.
 * </p>
 *
 * <h2>Core Components</h2>
 *
 * <h3>Stability Levels</h3>
 * <p>
 * The {@link org.wildfly.prospero.stability.Stability} enum defines four stability levels in order
 * from least to most restrictive:
 * </p>
 * <ul>
 * <li><strong>{@link org.wildfly.prospero.stability.Stability#Experimental}</strong> - Unstable, experimental features</li>
 * <li><strong>{@link org.wildfly.prospero.stability.Stability#Preview}</strong> - Preview features with some stability</li>
 * <li><strong>{@link org.wildfly.prospero.stability.Stability#Community}</strong> - Community-supported features</li>
 * <li><strong>{@link org.wildfly.prospero.stability.Stability#Default}</strong> - Production-ready, stable features</li>
 * </ul>
 *
 * <h3>Annotations</h3>
 * <p>
 * The {@link org.wildfly.prospero.stability.StabilityLevel} annotation is used to mark classes, methods,
 * and fields with stability requirements. The annotation processing is handled by various
 * components throughout the system.
 * </p>
 *
 * <h3>Runtime Enforcement</h3>
 * <p>
 * The {@link org.wildfly.prospero.stability.StabilityUtils} class provides runtime enforcement of
 * stability restrictions, primarily used in API proxy implementations. The
 * {@link org.wildfly.prospero.stability.StabilityAwareInvocationHandler} implements dynamic proxy
 * behavior to automatically enforce stability restrictions on API method calls.
 * </p>
 *
 * <h2>Usage Patterns</h2>
 *
 * <h3>Marking Features</h3>
 * <pre>{@code
 * // Command that requires Community level or lower
 * @StabilityLevel(level = Stability.Community)
 * @CommandLine.Command(name = "advanced-feature")
 * public class AdvancedCommand extends AbstractCommand { ... }
 *
 * // Option only available at Experimental level
 * @CommandLine.Option(names = "--experimental")
 * @StabilityLevel(level = Stability.Experimental)
 * boolean experimentalMode;
 *
 * // API method with stability restriction
 * @StabilityLevel(level = Stability.Preview)
 * public void previewOperation() { ... }
 * }</pre>
 *
 * <h3>Checking Permissions</h3>
 * <pre>{@code
 * // Check if current stability permits a feature
 * Stability current = ionInfo.getStability();
 * if (current.permits(Stability.Community)) {
 *     // Enable community-level features
 * }
 *
 * // Runtime enforcement in API
 * StabilityUtils.ensureAllowed(Stability.Preview, "MyClass", "previewMethod");
 *
 * // Creating a stability-aware proxy
 * ProsperoInstallationManager target = new ProsperoInstallationManager(...);
 * StabilityAwareInvocationHandler handler = new StabilityAwareInvocationHandler(target);
 * InstallationManager proxy = (InstallationManager) Proxy.newProxyInstance(
 *     InstallationManager.class.getClassLoader(),
 *     new Class[]{InstallationManager.class},
 *     handler
 * );
 * }</pre>
 *
 * <h3>Setting Override Levels</h3>
 * <pre>{@code
 * // Set stability override (typically from CLI arguments)
 * try {
 *     ionInfo.setStability(Stability.Community);
 * } catch (IllegalStateException e) {
 *     // Handle restriction violations
 * }
 * }</pre>
 *
 * <h2>Integration Points</h2>
 *
 * <h3>Command Line Interface</h3>
 * <p>
 * The CLI integration is handled by {@link org.wildfly.prospero.cli.StabilityAwareCommandBuilder},
 * which filters commands and options based on stability annotations.
 * </p>
 *
 * <h3>API Layer</h3>
 * <p>
 * API integration uses dynamic proxies created with {@link org.wildfly.prospero.stability.StabilityAwareInvocationHandler}
 * that check method annotations and enforce restrictions through {@link org.wildfly.prospero.stability.StabilityUtils}.
 * The invocation handler automatically intercepts method calls and validates stability permissions
 * before delegating to the target implementation.
 * </p>
 *
 * <h3>Testing Framework</h3>
 * <p>
 * Test integration is provided by {@link org.wildfly.utils.StabilityLevelAwareRunner},
 * which skips tests that require unavailable stability levels.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * All components in this package are designed to be thread-safe:
 * </p>
 * <ul>
 * <li>{@link org.wildfly.prospero.DistributionInfo} uses proper synchronization for stability management</li>
 * <li>{@link org.wildfly.prospero.stability.StabilityUtils} is stateless and thread-safe</li>
 * <li>{@link org.wildfly.prospero.stability.StabilityAwareInvocationHandler} preserves thread safety of target objects</li>
 * <li>{@link org.wildfly.prospero.stability.Stability} is an immutable enum</li>
 * <li>{@link org.wildfly.prospero.stability.StabilityLevel} is a pure annotation</li>
 * </ul>
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 * <li>Stability levels are cached to avoid repeated manifest parsing</li>
 * <li>Command building caches stability for consistent filtering</li>
 * <li>Annotation processing uses reflection but results can be cached</li>
 * <li>Double-checked locking minimizes synchronization overhead</li>
 * </ul>
 *
 * @since 1.4.0
 * @author Bartosz Spyrko-Śmietanko <bspyrkos@redhat.com>
 */
package org.wildfly.prospero.stability;