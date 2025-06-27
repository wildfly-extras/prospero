package org.wildfly.prospero.stability;

import org.wildfly.prospero.DistributionInfo;

/**
 * Utility class for runtime stability level enforcement.
 * <p>
 * This class provides methods to check and enforce stability level restrictions at runtime,
 * primarily used by the API proxy system to prevent access to operations that are not
 * permitted at the current distribution's stability level.
 * </p>
 *
 * <h3>Usage</h3>
 * <p>
 * The primary use case is in dynamic proxies that wrap API classes to enforce stability
 * restrictions based on method annotations:
 * </p>
 * <pre>{@code
 * // In a dynamic proxy InvocationHandler
 * public Object invoke(Object proxy, Method method, Object[] args) {
 *     StabilityLevel annotation = method.getAnnotation(StabilityLevel.class);
 *     if (annotation != null) {
 *         StabilityUtils.ensureAllowed(annotation.level(),
 *                                    targetClass.getName(),
 *                                    method.getName());
 *     }
 *     return method.invoke(target, args);
 * }
 * }</pre>
 *
 * <h3>Thread Safety</h3>
 * <p>
 * This class is thread-safe. The {@link #ensureAllowed(Stability, String, String)} method
 * can be called concurrently from multiple threads without synchronization.
 * </p>
 *
 * @since 1.0
 * @see StabilityLevel
 * @see DistributionInfo#getStability()
 * @see org.wildfly.prospero.spi.ProsperoInstallationManagerFactory
 */
public class StabilityUtils {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private StabilityUtils() {
        // Utility class - no instances allowed
    }

    /**
     * Ensures that the current distribution stability level permits access to the specified operation.
     * <p>
     * This method checks if the current distribution's stability level permits access to
     * operations requiring the specified minimum stability level. If access is not permitted,
     * an {@link UnsupportedOperationException} is thrown with a descriptive error message.
     * </p>
     *
     * <h4>Example Usage</h4>
     * <pre>{@code
     * // Ensure current stability permits Community-level operations
     * StabilityUtils.ensureAllowed(Stability.Community,
     *                            "InstallationManager",
     *                            "experimentalFeature");
     * }</pre>
     *
     * @param minLevel the minimum stability level required for the operation
     * @param className the name of the class containing the operation (for error messages)
     * @param methodName the name of the method being accessed (for error messages)
     *
     * @throws UnsupportedOperationException if the current stability level does not permit
     *         access to operations requiring the specified minimum level
     * @throws NullPointerException if any parameter is null
     *
     * @see Stability#permits(Stability)
     * @see DistributionInfo#getStability()
     */
    public static void ensureAllowed(Stability minLevel, String className, String methodName) {
        if (minLevel == null) {
            throw new NullPointerException("minLevel cannot be null");
        }
        if (className == null) {
            throw new NullPointerException("className cannot be null");
        }
        if (methodName == null) {
            throw new NullPointerException("methodName cannot be null");
        }

        final Stability currentStability = DistributionInfo.getStability();
        if (!currentStability.permits(minLevel)) {
            throw new UnsupportedOperationException("The operation %s.%s is not supported at the current stability level %s.".formatted(
                    className, methodName, currentStability));
        }
    }
}
