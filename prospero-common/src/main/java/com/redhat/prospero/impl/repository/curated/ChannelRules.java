package com.redhat.prospero.impl.repository.curated;

import org.eclipse.aether.version.Version;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static java.lang.Integer.parseInt;

public class ChannelRules {

    private Map<String, Policy> policies = new HashMap<>();

    public static Policy version(String strictVersion) {
        return new StrictVersionPolicy(strictVersion);
    }

    public void allow(String ga, Policy policy) {
        policies.put(ga, policy);
    }

    public Policy getPolicy(String ga) {
        if (policies.isEmpty()) {
            return NamedPolicy.ANY;
        }
        return policies.getOrDefault(ga, NamedPolicy.ANY);
    }

    public enum NamedPolicy implements Policy {
        ANY((v, baseVersion)->true), MICRO((v, baseVersion) -> {
            final String[] mmm1 = baseVersion.split("[.\\-_]");
            final String[] mmm2 = v.toString().split("[.\\-_]");

            if (parseInt(mmm1[0]) != parseInt(mmm2[0]) || parseInt(mmm1[1]) != parseInt(mmm2[1])) {
                return false;
            } else {
                return true;
            }
        });

        private final BiPredicate<? super Version, String> filter;

        public Predicate<? super Version> getFilter(String baseVersion) {
            return (v)->this.filter.test(v, baseVersion);
        }

        NamedPolicy(BiPredicate<? super Version, String> filter) {
            this.filter = filter;
        }

    }

    public interface Policy {
        Predicate<? super Version> getFilter(String baseVersion);
    }

    static class StrictVersionPolicy implements Policy {

        private final String strictVersion;

        StrictVersionPolicy(String strictVersion) {
            this.strictVersion = strictVersion;
        }

        @Override
        public Predicate<? super Version> getFilter(String baseVersion) {
            return (v)->v.toString().equals(strictVersion);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StrictVersionPolicy that = (StrictVersionPolicy) o;
            return Objects.equals(strictVersion, that.strictVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(strictVersion);
        }
    }
}
