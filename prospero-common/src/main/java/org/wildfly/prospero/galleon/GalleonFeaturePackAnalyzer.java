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
import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.layout.FeaturePackLayout;
import org.jboss.galleon.layout.ProvisioningLayout;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.jboss.galleon.spec.FeaturePackPlugin;
import org.jboss.galleon.util.HashUtils;
import org.wildfly.channel.Channel;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GalleonFeaturePackAnalyzer {

    private final List<Channel> channels;
    private final MavenSessionManager mavenSessionManager;

    public GalleonFeaturePackAnalyzer(List<Channel> channels, MavenSessionManager mavenSessionManager) {
        this.channels = channels;
        this.mavenSessionManager = mavenSessionManager;
    }

    /**
     * Analyzes provisioning information found in {@code installedDir} and caches {@code FeaturePack} and Galleon plugin
     * artifacts.
     *
     * This complements caching done in <a href="https://github.com/wildfly/galleon-plugins/blob/main/galleon-plugins/src/main/java/org/wildfly/galleon/plugin/ArtifactRecorder.java">Wildfly Galleon Plugin</a>},
     * as Galleon plugin is not able to access FeaturePack information. The discovered artifacts are cached using {@link ArtifactCache}.
     *
     * @param installedDir - path to the installation. Used to access the cache
     * @param provisioningConfig - Galleon configuration to analyze
     */
    public void cacheGalleonArtifacts(Path installedDir, ProvisioningConfig provisioningConfig) throws Exception {
        // no data will be actually written out, but we need a path to init the Galleon
        final Path tempInstallationPath = Files.createTempDirectory("temp");
        final List<String> fps = new ArrayList<>();

        GalleonEnvironment galleonEnv = null;
        ProvisioningLayoutFactory layoutFactory = null;
        ProvisioningLayout<FeaturePackLayout> layout = null;
        try {
            galleonEnv = galleonEnvWithFpMapper(tempInstallationPath, fps);
            final ProvisioningManager pm = galleonEnv.getProvisioningManager();


            layoutFactory = pm.getLayoutFactory();
            layout = layoutFactory.newConfigLayout(provisioningConfig);


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

            try {
                // cache wildfly-config-gen as it's not added in galleon-plugin - TODO: remove when fixed in galleon-plugins
                final MavenArtifact mavenArtifact = galleonEnv.getChannelSession().resolveMavenArtifact("org.wildfly.galleon-plugins", "wildfly-config-gen", "jar", null, null);
                artifactCache.cache(mavenArtifact);
            } catch (UnresolvedMavenArtifactException e) {
                // ignore - wildfly-config-gen has not been defined
            }

            updateHashes(installedDir);
        } finally {
            if (layout != null) {
                layout.close();
            }
            if (layoutFactory != null) {
                layoutFactory.close();
            }
            if (galleonEnv != null) {
                galleonEnv.close();
            }
            FileUtils.deleteQuietly(tempInstallationPath.toFile());
        }
    }

    private void updateHashes(Path installedDir) throws IOException {
        final Path hashesFile = installedDir.resolve(Constants.PROVISIONED_STATE_DIR).resolve(Constants.HASHES)
                .resolve(ArtifactCache.CACHE_FOLDER).resolve(Constants.HASHES);
        final Path cachesDir = installedDir.resolve(ArtifactCache.CACHE_FOLDER);

        StringBuilder sb = new StringBuilder();
        for (File file : cachesDir.toFile().listFiles()) {
            sb.append(file.getName()).append(System.lineSeparator());
            sb.append(HashUtils.bytesToHexString(HashUtils.hashPath(file.toPath()))).append(System.lineSeparator());
        }
        Files.writeString(hashesFile, sb.toString());
    }

    /**
     * lists maven coordinates (groupId:artifactId) of FeaturePacks included in the {@code provisioningConfig}. Includes transitive dependencies.
     *
     * @param provisioningConfig - provisioning config to analyze
     * @return
     * @throws IOException
     * @throws ProvisioningException
     * @throws OperationException
     */
    public List<String> getFeaturePacks(ProvisioningConfig provisioningConfig) throws IOException, ProvisioningException, OperationException {
        // no data will be actually written out, but we need a path to init the Galleon
        final Path tempInstallationPath = Files.createTempDirectory("temp");
        final List<String> fps = new ArrayList<>();
        try (GalleonEnvironment galleonEnv = galleonEnvWithFpMapper(tempInstallationPath, fps)) {
            final ProvisioningManager pm = galleonEnv.getProvisioningManager();

            final ProvisioningLayoutFactory layoutFactory = pm.getLayoutFactory();
            layoutFactory.newConfigLayout(provisioningConfig).close();
            return fps;
        } finally {
            FileUtils.deleteQuietly(tempInstallationPath.toFile());
        }
    }

    private GalleonEnvironment galleonEnvWithFpMapper(Path tempInstallationPath, List<String> fps) throws ProvisioningException, OperationException {
        final GalleonEnvironment galleonEnv = GalleonEnvironment
                .builder(tempInstallationPath, channels, mavenSessionManager)
                .setConsole(null)
                .setResolvedFpTracker(fps::add)
                .build();
        return galleonEnv;
    }

}
