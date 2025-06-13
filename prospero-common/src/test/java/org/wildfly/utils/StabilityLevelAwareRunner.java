package org.wildfly.utils;

import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;
import org.wildfly.prospero.DistributionInfo;
import org.wildfly.prospero.Stability;
import org.wildfly.prospero.StabilityLevel;

/**
 * Ignores tests that have stability level lower than allowed by the project
 */
public class StabilityLevelAwareRunner extends BlockJUnit4ClassRunner {

    public StabilityLevelAwareRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    public StabilityLevelAwareRunner(TestClass testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected boolean isIgnored(FrameworkMethod child) {
        if (!DistributionInfo.getStability().permits(getMethodStabilityLevel(child))) {
            return true;
        } else {
            return super.isIgnored(child);
        }
    }

    private Stability getMethodStabilityLevel(FrameworkMethod child) {
        final StabilityLevel methodLevel = child.getAnnotation(StabilityLevel.class);
        if (methodLevel != null) {
            return methodLevel.level();
        }

        final StabilityLevel classLevel = getTestClass().getAnnotation(StabilityLevel.class);
        if (classLevel != null) {
            return classLevel.level();
        }

        return Stability.Default;
    }
}
