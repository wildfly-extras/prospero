package org.wildfly.prospero;

public class StabilityUtils {
    public static void ensureAllowed(Stability minLevel, String className, String methodName) {
        final Stability currentStability = DistributionInfo.getStability();
        if (!currentStability.permits(minLevel)) {
            throw new UnsupportedOperationException("The operation %s.%s is not supported at the current stability level %s.".formatted(
                    className, methodName, currentStability));
        }
    }
}
