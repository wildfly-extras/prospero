package org.wildfly.prospero.api;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;
import org.wildfly.prospero.model.KnownFeaturePack;

/**
 * Defines well known Galleon feature packs.
 */
public abstract class KnownFeaturePacks {

    private static final Logger logger = Logger.getLogger(KnownFeaturePacks.class);

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
        final URL knownRepoUrl = KnownFeaturePacks.class.getClassLoader().getResource("prospero-known-combinations.yaml");
        if (knownRepoUrl == null) {
            logger.debug("No known repositories found");
        } else {
            logger.debug("Loading known provisioning configurations from: " + knownRepoUrl);
            final List<KnownFeaturePack> knownFeaturePacks;
            try {
                knownFeaturePacks = KnownFeaturePack.readConfig(knownRepoUrl);
                for (KnownFeaturePack fp : knownFeaturePacks) {
                    nameMap.put(fp.getName(), fp);
                }
            } catch (IOException e) {
                logger.warn("Failed to load provisioning configurations from: " + knownRepoUrl);
                logger.debug("Error parsing provisioning configurations:", e);
            }
        }
    }

}
