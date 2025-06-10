package org.wildfly.prospero;

public class StabilityUtils {
    public static void ensureAllowed(Stability minLevel, String className, String methodName) {
        final Stability currentStability = DistributionInfo.getStability();
        if (!currentStability.permits(minLevel)) {
            throw new IllegalAccessError("The operation %s.%s is not allowed at a current stability level %s.".formatted(
                    className, methodName, currentStability));
        }
    }
}
