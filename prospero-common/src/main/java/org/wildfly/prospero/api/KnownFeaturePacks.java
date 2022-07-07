package org.wildfly.prospero.api;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.wildfly.prospero.model.KnownFeaturePack;

/**
 * Defines well known Galleon feature packs.
 */
public abstract class KnownFeaturePacks {

    private static final Map<String, KnownFeaturePack> nameMap = new HashMap<>();

    public static KnownFeaturePack getByName(String name) {
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
        try {
            final URL resource = KnownFeaturePacks.class.getClassLoader().getResource("known-repositories.yaml");

            final List<KnownFeaturePack> knownFeaturePacks = KnownFeaturePack.readConfig(resource);
            for (KnownFeaturePack fp : knownFeaturePacks) {
                nameMap.put(fp.getName(), fp);
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
