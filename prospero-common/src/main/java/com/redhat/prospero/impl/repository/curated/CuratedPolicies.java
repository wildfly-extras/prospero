/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.prospero.impl.repository.curated;

import org.eclipse.aether.version.Version;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static java.lang.Integer.parseInt;

public class CuratedPolicies {
    public enum Policy {
        MICRO;

        public Predicate<? super Version> getFilter(String baseVersion) {
            return (v) -> {
                final String[] mmm1 = baseVersion.split("[.\\-_]");
                final String[] mmm2 = v.toString().split("[.\\-_]");

                if (parseInt(mmm1[0]) != parseInt(mmm2[0]) || parseInt(mmm1[1]) != parseInt(mmm2[1])) {
                    return false;
                } else {
                    return true;
                }
            };
        }
    }
    private Map<String, Policy> policies = new HashMap<>();

    public void allow(String ga, Policy policy) {
        policies.put(ga, policy);
    }

    public Policy getPolicy(String ga) {
        return policies.get(ga);
    }
}
