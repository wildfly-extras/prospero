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
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProvisioningConfig;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.patch.Patch;
import org.wildfly.prospero.patch.PatchArchive;
import org.wildfly.prospero.wfchannel.ChannelRefUpdater;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.wildfly.prospero.patch.PatchArchive.PATCH_REPO_FOLDER;

public class ApplyPatch {
    public static final String PATCH_REPO_NAME = "patch-cache";
    public static final String PATCHES_FOLDER = ".patches";
    public static final Path PATCHES_REPO_PATH = Paths.get(PATCHES_FOLDER, PATCH_REPO_FOLDER);
    private final Path installDir;
    private final InstallationMetadata metadata;
    private final MavenSessionManager mavenSessionManager;

    public ApplyPatch(Path targetPath, MavenSessionManager mavenSessionManager, Console console) throws OperationException {
        this.installDir = targetPath;
        this.metadata = new InstallationMetadata(targetPath);
        this.mavenSessionManager = mavenSessionManager;
    }

    public void apply(Path patchArchive) throws OperationException, ProvisioningException {
        // TODO: verify archive
        // TODO: add console logging

        try {
            // install patch locally
            final Patch patch = new PatchArchive().extract(patchArchive.toFile(), installDir);

            // update config
            final ProvisioningConfig provisioningConfig = metadata.getProsperoConfig();
            provisioningConfig.addChannel(new ChannelRef(null, patch.getChannelFileUrl().toString()));

            // add cached repository to config if not present
            provisioningConfig.addRepository(new RepositoryRef(PATCH_REPO_NAME, installDir.resolve(PATCHES_REPO_PATH).toUri().toURL().toString()));
            metadata.updateProsperoConfig(provisioningConfig);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (MetadataException e) {
            e.printStackTrace();
        }

        reprovisionServer();
    }

    private void reprovisionServer() throws MetadataException, ArtifactResolutionException, ProvisioningException {
        // have to parse the config after the patch metadata was applied
        final ProvisioningConfig prosperoConfig = metadata.getProsperoConfig();
        final List<RemoteRepository> repositories = prosperoConfig.getRemoteRepositories();
        final List<Channel> channels = mapToChannels(new ChannelRefUpdater(mavenSessionManager)
                .resolveLatest(prosperoConfig.getChannels(), repositories));

        final RepositorySystem system = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = mavenSessionManager.newRepositorySystemSession(system);
        final VersionResolverFactory factory = new VersionResolverFactory(system, session, repositories);
        final ChannelSession channelSession = new ChannelSession(channels, factory);
        ChannelMavenArtifactRepositoryManager maven = new ChannelMavenArtifactRepositoryManager(channelSession);
        ProvisioningManager provMgr = GalleonUtils.getProvisioningManager(installDir, maven);

        GalleonUtils.executeGalleon(options -> provMgr.provision(provMgr.getProvisioningConfig(), options),
                mavenSessionManager.getProvisioningRepo().toAbsolutePath());

        metadata.setChannel(maven.resolvedChannel());

        metadata.writeFiles();
    }

    private List<Channel> mapToChannels(List<ChannelRef> channelRefs) throws MetadataException {
        final List<Channel> channels = new ArrayList<>();
        for (ChannelRef ref : channelRefs) {
            try {
                channels.add(ChannelMapper.from(new URL(ref.getUrl())));
            } catch (MalformedURLException e) {
                throw Messages.MESSAGES.unableToResolveChannelConfiguration(e);
            }
        }
        return channels;
    }
}
