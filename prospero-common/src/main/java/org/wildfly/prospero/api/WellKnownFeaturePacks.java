package org.wildfly.prospero.api;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
            ));

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

    public static boolean isNameKnown(String name) {
        return nameMap.containsKey(name);
    }

    static {
        nameMap.put("wildfly", WILDFLY);
        nameMap.put("eap", EAP_74); // TODO: should give the latest version?
        nameMap.put("eap-7.4", EAP_74);
        nameMap.put("eap-8.0", EAP_80);
    }

}
