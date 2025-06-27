package org.wildfly.prospero.cli;

import org.wildfly.prospero.DistributionInfo;
import org.wildfly.prospero.Stability;
import org.wildfly.prospero.StabilityLevel;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Centralized configuration and caching for stability-related operations.
 * This class provides efficient access to stability levels with caching
 * to avoid repeated reflection operations.
 *
 * @since 1.4.0
 */
public class StabilityConfiguration {

    private final Stability currentStability;
    private final ConcurrentMap<Class<?>, Stability> classStabilityCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Field, Stability> fieldStabilityCache = new ConcurrentHashMap<>();

    /**
     * Creates a new stability configuration using the current distribution stability.
     */
    public StabilityConfiguration() {
        this(DistributionInfo.getStability());
    }

    /**
     * Creates a new stability configuration with the specified stability level.
     * This constructor is primarily for testing purposes.
     *
     * @param stability the stability level to use
     */
    public StabilityConfiguration(Stability stability) {
        this.currentStability = stability;
    }

    /**
     * Returns the current stability level.
     *
     * @return the current stability level
     */
    public Stability getCurrentStability() {
        return currentStability;
    }

    /**
     * Determines if the given class is permitted at the current stability level.
     * Results are cached to improve performance.
     *
     * @param clazz the class to check
     * @return true if the class is permitted, false otherwise
     */
    public boolean isClassPermitted(Class<?> clazz) {
        Stability classLevel = getClassStabilityLevel(clazz);
        return currentStability.permits(classLevel);
    }

    /**
     * Determines if the given field is permitted at the current stability level.
     * Results are cached to improve performance.
     *
     * @param field the field to check
     * @return true if the field is permitted, false otherwise
     */
    public boolean isFieldPermitted(Field field) {
        Stability fieldLevel = getFieldStabilityLevel(field);
        return currentStability.permits(fieldLevel);
    }

    /**
     * Gets the stability level for a class, with caching.
     *
     * @param clazz the class to check
     * @return the stability level (defaults to Default if no annotation)
     */
    public Stability getClassStabilityLevel(Class<?> clazz) {
        return classStabilityCache.computeIfAbsent(clazz, this::computeClassStabilityLevel);
    }

    /**
     * Gets the stability level for a field, with caching.
     *
     * @param field the field to check
     * @return the stability level (defaults to Default if no annotation)
     */
    public Stability getFieldStabilityLevel(Field field) {
        return fieldStabilityCache.computeIfAbsent(field, this::computeFieldStabilityLevel);
    }

    private Stability computeClassStabilityLevel(Class<?> clazz) {
        StabilityLevel annotation = clazz.getAnnotation(StabilityLevel.class);
        return annotation != null ? annotation.level() : Stability.Default;
    }

    private Stability computeFieldStabilityLevel(Field field) {
        StabilityLevel annotation = field.getAnnotation(StabilityLevel.class);
        return annotation != null ? annotation.level() : Stability.Default;
    }

    /**
     * Clears all caches. Primarily for testing purposes.
     */
    public void clearCaches() {
        classStabilityCache.clear();
        fieldStabilityCache.clear();
    }

    /**
     * Returns cache statistics for monitoring purposes.
     *
     * @return formatted string with cache statistics
     */
    public String getCacheStats() {
        return String.format("StabilityConfiguration[classCache=%d, fieldCache=%d, currentStability=%s]",
                classStabilityCache.size(), fieldStabilityCache.size(), currentStability);
    }
}