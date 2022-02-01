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

package com.redhat.prospero.cli.actions;

import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.MetadataException;
import com.redhat.prospero.galleon.FeaturePackLocationParser;
import com.redhat.prospero.api.Server;
import com.redhat.prospero.galleon.GalleonUtils;
import com.redhat.prospero.galleon.ChannelMavenArtifactRepositoryManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.wfchannel.WfChannelMavenResolverFactory;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;

import static com.redhat.prospero.api.ArtifactUtils.from;

public class Installation {

    private Path installDir;

    public Installation(Path installDir) {
        this.installDir = installDir;
    }

    static {
        enableJBossLogManager();
    }

    private static void enableJBossLogManager() {
        if (System.getProperty("java.util.logging.manager") == null) {
            System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        }
    }

    public static void main(String[] args) throws ProvisioningException, IOException, MetadataException, ArtifactResolutionException, VersionRangeResolutionException {
        if (args.length < 2) {
            System.out.println("Not enough parameters. Need to provide FPL, output directory and channels file.");
            return;
        }
        final Server server = new Server(args[0], null);
        final Path base = Paths.get(args[1]).toAbsolutePath();

        new Installation(base).provision(server.getFpl(), server.getChannelRefs());
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

        final ChannelMavenArtifactRepositoryManager repoManager = createRepositoryManager(channels);
        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installDir, repoManager);
        FeaturePackLocation loc = new FeaturePackLocationParser(repoManager).resolveFpl(fpl);

        provMgr.install(loc);

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

        final ChannelMavenArtifactRepositoryManager repoManager = createRepositoryManager(channels);
        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installDir, repoManager);

        provMgr.provision(installationFile);

        writeProsperoMetadata(installDir, repoManager, channelRefs);
    }

    private ChannelMavenArtifactRepositoryManager createRepositoryManager(List<Channel> channels) {
        final WfChannelMavenResolverFactory factory = new WfChannelMavenResolverFactory();
        return new ChannelMavenArtifactRepositoryManager(channels, factory);
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
