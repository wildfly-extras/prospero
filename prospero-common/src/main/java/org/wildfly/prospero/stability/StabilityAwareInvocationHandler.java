package org.wildfly.prospero.stability;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.prospero.spi.ProsperoInstallationManager;

/**
 * A dynamic proxy invocation handler that enforces stability level restrictions on API method calls.
 * <p>
 * This class implements the {@link InvocationHandler} interface to create dynamic proxies that
 * wrap {@link InstallationManager} instances and enforce stability level restrictions at runtime.
 * When a method is invoked on the proxy, this handler checks if the method has a {@link StabilityLevel}
 * annotation and validates that the current distribution stability permits access to that method.
 * </p>
 *
 * <h3>How It Works</h3>
 * <p>
 * The handler operates by:
 * </p>
 * <ol>
 * <li>Intercepting method calls on the proxy object</li>
 * <li>Looking up the corresponding method on the target {@link ProsperoInstallationManager}</li>
 * <li>Checking for {@link StabilityLevel} annotations on the method</li>
 * <li>Validating that the current stability level permits the method call</li>
 * <li>Either forwarding the call to the target object or throwing an exception</li>
 * </ol>
 *
 * <h3>Stability Level Resolution</h3>
 * <p>
 * Methods are assigned stability levels according to the following rules:
 * </p>
 * <ul>
 * <li>If a method has a {@link StabilityLevel} annotation, that level is used</li>
 * <li>If no annotation is present, the method defaults to {@link Stability#Default}</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Create a stability-aware proxy
 * ProsperoInstallationManager target = new ProsperoInstallationManager(...);
 * StabilityAwareInvocationHandler handler = new StabilityAwareInvocationHandler(target);
 * InstallationManager proxy = (InstallationManager) Proxy.newProxyInstance(
 *     InstallationManager.class.getClassLoader(),
 *     new Class[]{InstallationManager.class},
 *     handler
 * );
 *
 * // Method calls are now stability-aware
 * proxy.someMethod(); // Will check stability before executing
 * }</pre>
 *
 * <h3>Error Handling</h3>
 * <p>
 * If a method call is not permitted at the current stability level, the handler throws
 * an {@link UnsupportedOperationException} with a descriptive error message indicating
 * which operation was attempted and what the current stability level is.
 * </p>
 *
 * <h3>Thread Safety</h3>
 * <p>
 * This class is thread-safe. Multiple threads can safely invoke methods on proxies
 * created with this handler, as the stability checking is stateless and the underlying
 * target object's thread safety is preserved.
 * </p>
 *
 * @since 1.4.0
 * @see StabilityLevel
 * @see StabilityUtils#ensureAllowed(Stability, String, String)
 * @see java.lang.reflect.Proxy
 * @see InvocationHandler
 */
public class StabilityAwareInvocationHandler implements InvocationHandler {
    /**
     * The target installation manager instance that actual method calls are delegated to.
     */
    private final ProsperoInstallationManager pim;

    /**
     * Creates a new stability-aware invocation handler for the specified installation manager.
     * <p>
     * The handler will intercept all method calls on proxy objects and enforce stability
     * level restrictions before delegating to the target installation manager.
     * </p>
     *
     * @param pim the target installation manager to wrap with stability checking
     * @throws NullPointerException if pim is null
     */
    public StabilityAwareInvocationHandler(ProsperoInstallationManager pim) {
        this.pim = java.util.Objects.requireNonNull(pim, "ProsperoInstallationManager cannot be null");
    }

    /**
     * Handles method invocations on the proxy object with stability level enforcement.
     * <p>
     * This method is called whenever a method is invoked on a proxy instance that uses
     * this invocation handler. It performs the following steps:
     * </p>
     * <ol>
     * <li>Looks up the corresponding method on the target {@link ProsperoInstallationManager}</li>
     * <li>Checks for a {@link StabilityLevel} annotation on that method</li>
     * <li>Determines the required stability level (defaults to {@link Stability#Default})</li>
     * <li>Validates that the current distribution stability permits the method call</li>
     * <li>Delegates the call to the target object if permitted</li>
     * </ol>
     *
     * @param proxy the proxy instance that the method was invoked on
     * @param method the method that was invoked on the proxy
     * @param args the arguments passed to the method invocation
     * @return the result of invoking the method on the target object
     *
     * @throws UnsupportedOperationException if the current stability level does not permit
     *         access to the invoked method
     * @throws NoSuchMethodException if the method cannot be found on the target object
     * @throws Throwable any exception thrown by the target method
     *
     * @see StabilityUtils#ensureAllowed(Stability, String, String)
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final StabilityLevel annotation = pim.getClass().getDeclaredMethod(method.getName(), method.getParameterTypes()).getAnnotation(StabilityLevel.class);
        final Stability level;
        if (annotation == null) {
            level = Stability.Default;
        } else {
            level = annotation.level();
        }
        StabilityUtils.ensureAllowed(level, InstallationManager.class.getName(), method.getName());

        return method.invoke(pim, args);
    }
}
