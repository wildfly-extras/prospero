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

package com.redhat.prospero.actions;

import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.MetadataException;
import com.redhat.prospero.cli.Console;
import com.redhat.prospero.galleon.FeaturePackLocationParser;
import com.redhat.prospero.galleon.GalleonUtils;
import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.wfchannel.WfChannelMavenResolverFactory;
import org.eclipse.aether.artifact.Artifact;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;

import static com.redhat.prospero.api.ArtifactUtils.from;

public class Installation {

    private Path installDir;
    private Console console;

    public Installation(Path installDir, Console console) {
        this.installDir = installDir;
        this.console = console;
    }

    static {
        enableJBossLogManager();
    }

    private static void enableJBossLogManager() {
        if (System.getProperty("java.util.logging.manager") == null) {
            System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        }
    }

    /**
     * Installs feature pack defined by {@code fpl} in {@code installDir}. If {@code fpl} doesn't include version,
     * the newest available version will be used.
     *
     * @param fpl
     * @param channelRefs
     * @throws ProvisioningException
     * @throws MetadataException
     */
    public void provision(String fpl, List<ChannelRef> channelRefs) throws ProvisioningException, MetadataException {
        final List<Channel> channels = getChannels(channelRefs);

        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory();
        final ChannelMavenArtifactRepositoryManager repoManager = new ChannelMavenArtifactRepositoryManager(channels, factory);
        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installDir, repoManager);
        final ProvisioningLayoutFactory layoutFactory = provMgr.getLayoutFactory();

        layoutFactory.setProgressCallback("LAYOUT_BUILD", console.getProgressCallback("LAYOUT_BUILD"));
        layoutFactory.setProgressCallback("PACKAGES", console.getProgressCallback("PACKAGES"));
        layoutFactory.setProgressCallback("CONFIGS", console.getProgressCallback("CONFIGS"));
        layoutFactory.setProgressCallback("JBMODULES", console.getProgressCallback("JBMODULES"));
        FeaturePackLocation loc = new FeaturePackLocationParser(repoManager).resolveFpl(fpl);

        console.println("Installing " + loc.toString());

        provMgr.install(loc, GalleonUtils.defaultOptions(factory));

        writeProsperoMetadata(installDir, repoManager, channelRefs);
    }

    /**
     * Installs feature pack based on Galleon installation file
     *
     * @param installationFile
     * @param channelRefs
     * @throws ProvisioningException
     * @throws IOException
     * @throws MetadataException
     */
    public void provision(Path installationFile, List<ChannelRef> channelRefs) throws ProvisioningException, MetadataException {
        if (Files.exists(installDir)) {
            throw new ProvisioningException("Installation dir " + installDir + " already exists");
        }
        final List<Channel> channels = getChannels(channelRefs);

        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory();
        final ChannelMavenArtifactRepositoryManager repoManager = new ChannelMavenArtifactRepositoryManager(channels, factory);
        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installDir, repoManager);

        provMgr.provision(installationFile, GalleonUtils.defaultOptions(factory));

        writeProsperoMetadata(installDir, repoManager, channelRefs);
    }

    private List<Channel> getChannels(List<ChannelRef> channelRefs) {
        return channelRefs.stream().map(ref -> {
            try {
                return ChannelMapper.from(new URL(ref.getUrl()));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());
    }

    private void writeProsperoMetadata(Path home, ChannelMavenArtifactRepositoryManager maven, List<ChannelRef> channelRefs) throws MetadataException {
        List<Artifact> artifacts = new ArrayList<>();
        for (MavenArtifact resolvedArtifact : maven.resolvedArtfacts()) {
            artifacts.add(from(resolvedArtifact));
        }

        new InstallationMetadata(home, artifacts, channelRefs).writeFiles();
    }
}
