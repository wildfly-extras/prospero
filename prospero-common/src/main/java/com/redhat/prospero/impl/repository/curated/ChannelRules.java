package com.redhat.prospero.impl.repository.curated;

import org.eclipse.aether.version.Version;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static java.lang.Integer.parseInt;

public class ChannelRules {

    private Map<String, Policy> policies = new HashMap<>();

    public void allow(String ga, Policy policy) {
        policies.put(ga, policy);
    }

    public Policy getPolicy(String ga) {
        if (policies.isEmpty()) {
            return Policy.ANY;
        }
        return policies.getOrDefault(ga, Policy.ANY);
    }

    public enum Policy {
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

        Policy(BiPredicate<? super Version, String> filter) {
            this.filter = filter;
        }

    }
}
