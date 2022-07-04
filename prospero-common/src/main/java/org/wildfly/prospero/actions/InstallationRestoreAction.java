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

package org.wildfly.prospero.actions;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import org.wildfly.prospero.model.ProvisioningConfig;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class InstallationRestoreAction {

    private final Path installDir;
    private MavenSessionManager mavenSessionManager;

    public InstallationRestoreAction(Path installDir, MavenSessionManager mavenSessionManager) {
        this.installDir = installDir;
        this.mavenSessionManager = mavenSessionManager;
    }

    public static void main(String[] args) throws Exception {

        String targetDir = args[0];
        String metadataBundle = args[1];

        new InstallationRestoreAction(Paths.get(targetDir), new MavenSessionManager()).restore(Paths.get(metadataBundle));
    }

    public void restore(Path metadataBundleZip)
            throws ProvisioningException, IOException, MetadataException {
        if (installDir.toFile().exists()) {
            throw Messages.MESSAGES.installationDirAlreadyExists(installDir);
        }

        final InstallationMetadata metadataBundle = InstallationMetadata.importMetadata(metadataBundleZip);
        final ProvisioningConfig prosperoConfig = metadataBundle.getProsperoConfig();
        final List<Channel> channels = mapToChannels(prosperoConfig.getChannels());
        final List<RemoteRepository> repositories = prosperoConfig.getRemoteRepositories();

        final RepositorySystem system = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = mavenSessionManager.newRepositorySystemSession(system);
        MavenVersionsResolver.Factory factory = new VersionResolverFactory(system, session, repositories);
        final ChannelMavenArtifactRepositoryManager repoManager = new ChannelMavenArtifactRepositoryManager(channels, factory, metadataBundle.getManifest());

        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installDir, repoManager);

        GalleonUtils.executeGalleon(options -> provMgr.provision(metadataBundle.getProvisioningConfig(), options),
                mavenSessionManager.getProvisioningRepo().toAbsolutePath());
        writeProsperoMetadata(repoManager, prosperoConfig.getChannels(), repositories);
    }

    private void writeProsperoMetadata(ChannelMavenArtifactRepositoryManager maven, List<ChannelRef> channelRefs, List<RemoteRepository> repositories)
            throws MetadataException {
        new InstallationMetadata(installDir, maven.resolvedChannel(), channelRefs, repositories).writeFiles();
    }

    private List<Channel> mapToChannels(List<ChannelRef> channelRefs) throws MetadataException {
        final List<Channel> channels = new ArrayList<>();
        for (ChannelRef ref : channelRefs) {
            try {
                channels.add(ChannelMapper.from(new URL(ref.getUrl())));
            } catch (MalformedURLException e) {
                throw Messages.MESSAGES.unableToResolveChannelConfiguration(e);
            }
        } return channels;
    }
}
