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
import com.redhat.prospero.api.ProvisioningDefinition;
import com.redhat.prospero.api.exceptions.OperationException;
import com.redhat.prospero.galleon.FeaturePackLocationParser;
import com.redhat.prospero.galleon.GalleonUtils;
import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.wfchannel.ChannelRefUpdater;
import com.redhat.prospero.wfchannel.MavenSessionManager;
import com.redhat.prospero.wfchannel.WfChannelMavenResolverFactory;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;

import static com.redhat.prospero.api.ArtifactUtils.from;
import static com.redhat.prospero.galleon.GalleonUtils.MAVEN_REPO_LOCAL;

public class Installation {

    private final MavenSessionManager mavenSessionManager;
    private Path installDir;
    private Console console;

    public Installation(Path installDir, MavenSessionManager mavenSessionManager, Console console) {
        this.installDir = installDir;
        this.console = console;
        this.mavenSessionManager = mavenSessionManager;
    }

    /**
     * Installs feature pack defined by {@code fpl} in {@code installDir}. If {@code fpl} doesn't include version,
     * the newest available version will be used.
     *
     * @param provisioningDefinition
     * @throws ProvisioningException
     * @throws MetadataException
     */
    public void provision(ProvisioningDefinition provisioningDefinition) throws ProvisioningException, OperationException {
        final List<ChannelRef> updatedRefs = new ChannelRefUpdater(mavenSessionManager).resolveLatest(provisioningDefinition.getChannelRefs());
        final List<Channel> channels = mapToChannels(updatedRefs);

        // TODO: figure out how to populate repositories
        final List<RemoteRepository> repositories = provisioningDefinition.getRepositories();

        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory(mavenSessionManager, repositories);
        final ChannelMavenArtifactRepositoryManager repoManager = new ChannelMavenArtifactRepositoryManager(channels, factory);
        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installDir, repoManager);
        final ProvisioningLayoutFactory layoutFactory = provMgr.getLayoutFactory();

        layoutFactory.setProgressCallback("LAYOUT_BUILD", console.getProgressCallback("LAYOUT_BUILD"));
        layoutFactory.setProgressCallback("PACKAGES", console.getProgressCallback("PACKAGES"));
        layoutFactory.setProgressCallback("CONFIGS", console.getProgressCallback("CONFIGS"));
        layoutFactory.setProgressCallback("JBMODULES", console.getProgressCallback("JBMODULES"));
        FeaturePackLocation loc = new FeaturePackLocationParser(repoManager).resolveFpl(provisioningDefinition.getFpl());

        console.println("Installing " + loc.toString());

        final FeaturePackConfig.Builder configBuilder = FeaturePackConfig.builder(loc);
        for (String includedPackage : provisioningDefinition.getIncludedPackages()) {
            configBuilder.includePackage(includedPackage);
        }
        final FeaturePackConfig config = configBuilder.build();

        try {
            System.setProperty(MAVEN_REPO_LOCAL, mavenSessionManager.getProvisioningRepo().toAbsolutePath().toString());
            provMgr.install(config);
        } finally {
            System.clearProperty(MAVEN_REPO_LOCAL);
        }

        writeProsperoMetadata(installDir, repoManager, updatedRefs);
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
        final List<Channel> channels = mapToChannels(channelRefs);

        // TODO: figure out how to populate repositories
        final List<RemoteRepository> repositories = Arrays.asList(new RemoteRepository.Builder("mrrc", null, "https://maven.repository.redhat.com").build());

        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory(mavenSessionManager, repositories);
        final ChannelMavenArtifactRepositoryManager repoManager = new ChannelMavenArtifactRepositoryManager(channels, factory);
        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installDir, repoManager);

        try {
            System.setProperty(MAVEN_REPO_LOCAL, mavenSessionManager.getProvisioningRepo().toAbsolutePath().toString());
            provMgr.provision(installationFile);
        }finally {
            System.clearProperty(MAVEN_REPO_LOCAL);
        }

        writeProsperoMetadata(installDir, repoManager, channelRefs);
    }

    private List<Channel> mapToChannels(List<ChannelRef> channelRefs) throws MetadataException {
        final List<Channel> channels = new ArrayList<>();
        for (ChannelRef ref : channelRefs) {
            try {
                channels.add(ChannelMapper.from(new URL(ref.getUrl())));
            } catch (MalformedURLException e) {
                throw new MetadataException("Unable to resolve channel configuration", e);
            }
        } return channels;
    }

    private void writeProsperoMetadata(Path home, ChannelMavenArtifactRepositoryManager maven, List<ChannelRef> channelRefs) throws MetadataException {
        List<Artifact> artifacts = new ArrayList<>();
        for (MavenArtifact resolvedArtifact : maven.resolvedArtfacts()) {
            artifacts.add(from(resolvedArtifact));
        }

        new InstallationMetadata(home, artifacts, channelRefs).writeFiles();
    }
}
