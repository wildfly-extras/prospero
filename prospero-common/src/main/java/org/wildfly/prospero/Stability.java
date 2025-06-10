package org.wildfly.prospero;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum Stability {
    // NOTE: the order of the enums defines which scope permits other
    // keep the left->right order from the least restrictive to the most restrictive
    Preview, Community, Default;

    public static Stability from(String stability) {
        return Arrays.stream(Stability.values())
                .filter(s->s.toString().equalsIgnoreCase(stability))
                .findFirst()
                .orElseThrow(()->new IllegalArgumentException(
                        "Unknown stability level %s. Allowed values are %s".formatted(
                                stability, allowedValues()
                        )));
    }

    private static String allowedValues() {
        return Arrays.stream(Stability.values()).map(Stability::toString).map(String::toLowerCase).collect(Collectors.joining());
    }

    public boolean permits(Stability other) {
        return this.ordinal() <= other.ordinal();
    }
}
