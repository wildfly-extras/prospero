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
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.util.HashUtils;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.channel.Channel;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GalleonArtifactExporter {
    public void cacheGalleonArtifacts(List<Channel> channels, MavenSessionManager mavenSessionManager, Path installedDir, ProvisioningConfig provisioningConfig) throws Exception {
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
            for (String pluginGav : pluginGavs) {
                final String[] pluginLoc = pluginGav.split(":");
                final MavenArtifact jar = galleonEnv.getChannelSession().resolveMavenArtifact(pluginLoc[0], pluginLoc[1], "jar", null, null);
                cache(jar, installedDir);
            }

            for (String fp : fps) {
                // resolve the artifact
                final MavenArtifact mavenArtifact = galleonEnv.getChannelSession().resolveMavenArtifact(fp.split(":")[0], fp.split(":")[1], "zip", null, null);
                // cache it in the
                cache(mavenArtifact, installedDir);
            }
        } finally {
            FileUtils.deleteQuietly(tempInstallationPath.toFile());
        }
    }

    private void record(MavenArtifact artifact, Path target, Path installedDir) throws IOException {
        final String hash = HashUtils.hashFile(target);
        Files.writeString(installedDir.resolve(CachedVersionResolver.CACHE_FOLDER).resolve("artifacts.txt"),
                String.format("%s:%s:%s:%s", artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getVersion())
                        + "::" + hash + "::" + installedDir.relativize(target) + System.lineSeparator(), StandardOpenOption.APPEND);
    }

    private void cache(MavenArtifact artifact, Path installedDir) throws MavenUniverseException, IOException {
        final Path cacheDir = installedDir.resolve(CachedVersionResolver.CACHE_FOLDER);

        IoUtils.copy(artifact.getFile().toPath(), cacheDir.resolve(artifact.getFile().getName()));

        record(artifact, cacheDir.resolve(artifact.getFile().getName()), installedDir);
    }
}
