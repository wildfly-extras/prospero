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

package org.wildfly.prospero.api;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.aether.repository.RemoteRepository;

/**
 * Enumeration defining well known Galleon feature packs.
 */
public enum WellKnownFeaturePacks {

    WILDFLY("wildfly@maven(org.jboss.universe:community-universe):current",
            "org.wildfly.channels:wildfly:26.1.0",
            Arrays.asList(),
            Arrays.asList(WellKnownRepositories.CENTRAL.get(), WellKnownRepositories.JBOSS_PUBLIC.get())),

    EAP_74("org.jboss.eap:wildfly-ee-galleon-pack",
            "org.wildfly.channels:eap-74:7.4",
            Arrays.asList("docs.examples.configs"),
            Arrays.asList(WellKnownRepositories.MRRC.get())),

    EAP_80("org.jboss.eap:wildfly-ee-galleon-pack",
            "org.jboss.eap.channels:eap-8.0-beta:1.0.0.Beta-redhat-00001",
            Arrays.asList("docs.examples.configs"),
            Arrays.asList(
                    WellKnownRepositories.MRRC.get(),
                    WellKnownRepositories.CENTRAL.get() // EAP 8 is currently mix of productized and community artifacts
            )),

    EAP_XP_50("org.jboss.eap:wildfly-galleon-pack",
            "org.jboss.eap.channels:eap-xp-5.0-beta:1.0.0.Beta-redhat-00001", // TODO: channel GAV still unknown
            Arrays.asList("docs.examples.configs"),
            Arrays.asList(WellKnownRepositories.MRRC.get()));

    private static final Map<String, WellKnownFeaturePacks> nameMap = new HashMap<>();
    public final String location;
    public final String channelGav;
    public final Collection<String> packages;
    public final Collection<RemoteRepository> repositories;

    private WellKnownFeaturePacks(String location, String channelGav, Collection<String> packages,
            Collection<RemoteRepository> repositories) {
        Objects.requireNonNull(location);
        Objects.requireNonNull(packages);
        Objects.requireNonNull(repositories);
        Objects.requireNonNull(channelGav);

        this.location = location;
        this.channelGav = channelGav;
        this.packages = packages;
        this.repositories = repositories;
    }

    public static WellKnownFeaturePacks getByName(String name) {
        return nameMap.get(name);
    }

    public static boolean isWellKnownName(String name) {
        return nameMap.containsKey(name);
    }

    public static Set<String> getNames() {
        return nameMap.keySet();
    }

    static {
        //
        // Update also the list of names in UsageMessages.properties when modifying this.
        //
        nameMap.put("wildfly", WILDFLY);
        nameMap.put("eap", EAP_80);
        nameMap.put("eap-7.4", EAP_74);
        nameMap.put("eap-8.0", EAP_80);
        nameMap.put("xp-5.0", EAP_XP_50);
        nameMap.put("xp", EAP_XP_50);
    }

}
