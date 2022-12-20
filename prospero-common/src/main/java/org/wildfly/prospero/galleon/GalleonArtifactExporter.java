/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.galleon;

import org.apache.commons.io.FileUtils;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.layout.FeaturePackLayout;
import org.jboss.galleon.layout.ProvisioningLayout;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.jboss.galleon.spec.FeaturePackPlugin;
import org.wildfly.channel.Channel;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Analyzes provisioning information found in {@code installedDir} and caches {@code FeaturePack} and Galleon plugin
 * artifacts.
 *
 * This complements caching done in <a href="https://github.com/wildfly/galleon-plugins/blob/main/galleon-plugins/src/main/java/org/wildfly/galleon/plugin/ArtifactRecorder.java">Wildfly Galleon Plugin</a>},
 * as Galleon plugin is not able to access FeaturePack information. The discovered artifacts are cached using {@link ArtifactCache}.
 */
public class GalleonArtifactExporter {
    public void cacheGalleonArtifacts(List<Channel> channels, MavenSessionManager mavenSessionManager, Path installedDir, ProvisioningConfig provisioningConfig) throws Exception {
        // no data will be actually written out, but we need a path to init the Galleon
        final Path tempInstallationPath = Files.createTempDirectory("temp");
        try {
            final List<String> fps = new ArrayList<>();
            final GalleonEnvironment galleonEnv = GalleonEnvironment
                    .builder(tempInstallationPath, channels, mavenSessionManager)
                    .setConsole(null)
                    .setResolvedFpTracker(fps::add)
                    .build();
            final ProvisioningManager pm = galleonEnv.getProvisioningManager();


            final ProvisioningLayoutFactory layoutFactory = pm.getLayoutFactory();
            final ProvisioningLayout<FeaturePackLayout> layout = layoutFactory.newConfigLayout(provisioningConfig);

            Set<String> pluginGavs = new HashSet<>();
            for (FeaturePackLayout fp : layout.getOrderedFeaturePacks()) {
                for (FeaturePackPlugin plugin : fp.getSpec().getPlugins().values()) {
                    pluginGavs.add(plugin.getLocation());
                }
            }

            final ArtifactCache artifactCache = ArtifactCache.getInstance(installedDir);
            for (String pluginGav : pluginGavs) {
                final String[] pluginLoc = pluginGav.split(":");
                final MavenArtifact jar = galleonEnv.getChannelSession().resolveMavenArtifact(pluginLoc[0], pluginLoc[1], "jar", null, null);
                artifactCache.cache(jar);
            }

            for (String fp : fps) {
                // resolve the artifact
                final String[] fpLoc = fp.split(":");
                final MavenArtifact mavenArtifact = galleonEnv.getChannelSession().resolveMavenArtifact(fpLoc[0], fpLoc[1], "zip", null, null);
                // cache it in the
                artifactCache.cache(mavenArtifact);
            }
        } finally {
            FileUtils.deleteQuietly(tempInstallationPath.toFile());
        }
    }

}
