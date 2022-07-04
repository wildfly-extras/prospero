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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.galleon.ChannelMavenArtifactRepositoryManager;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProvisioningConfig;
import org.wildfly.prospero.updates.UpdateFinder;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.ChannelRefUpdater;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.ChannelSession;

public class UpdateAction {

    private final InstallationMetadata metadata;
    private final ChannelMavenArtifactRepositoryManager maven;
    private final ProvisioningManager provMgr;
    private final ChannelSession channelSession;

    private final Console console;
    private final MavenVersionsResolver.Factory factory;
    private final MavenSessionManager mavenSessionManager;

    public UpdateAction(Path installDir, MavenSessionManager mavenSessionManager, Console console) throws ProvisioningException, OperationException {
        this.metadata = new InstallationMetadata(installDir);

        this.mavenSessionManager = mavenSessionManager;
        final ProvisioningConfig prosperoConfig = metadata.getProsperoConfig();
        final List<RemoteRepository> repositories = prosperoConfig.getRemoteRepositories();
        final List<Channel> channels = mapToChannels(new ChannelRefUpdater(this.mavenSessionManager)
                .resolveLatest(prosperoConfig.getChannels(), repositories));

        final RepositorySystem system = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = mavenSessionManager.newRepositorySystemSession(system);
        this.factory = new VersionResolverFactory(system, session, repositories);
        this.channelSession = new ChannelSession(channels, factory);
        this.maven = new ChannelMavenArtifactRepositoryManager(channelSession);
        this.provMgr = GalleonUtils.getProvisioningManager(installDir, maven);
        final ProvisioningLayoutFactory layoutFactory = provMgr.getLayoutFactory();

        layoutFactory.setProgressCallback("LAYOUT_BUILD", console.getProgressCallback("LAYOUT_BUILD"));
        layoutFactory.setProgressCallback("PACKAGES", console.getProgressCallback("PACKAGES"));
        layoutFactory.setProgressCallback("CONFIGS", console.getProgressCallback("CONFIGS"));
        layoutFactory.setProgressCallback("JBMODULES", console.getProgressCallback("JBMODULES"));
        this.console = console;
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

    public void doUpdateAll(boolean confirmed) throws ProvisioningException, MetadataException, ArtifactResolutionException {
        final UpdateSet updateSet = findUpdates();

        console.updatesFound(updateSet.getFpUpdates().getUpdates(), updateSet.getArtifactUpdates());
        if (updateSet.isEmpty()) {
            return;
        }

        if (!confirmed && !console.confirmUpdates()) {
            return;
        }

        applyUpdates();

        metadata.writeFiles();

        console.updatesComplete();
    }

    public void listUpdates() throws ArtifactResolutionException, ProvisioningException {
        final UpdateSet updateSet = findUpdates();

        console.updatesFound(updateSet.getFpUpdates().getUpdates(), updateSet.getArtifactUpdates());
    }

    protected UpdateSet findUpdates() throws ArtifactResolutionException, ProvisioningException {
        try (final UpdateFinder updateFinder = new UpdateFinder(channelSession, provMgr)) {
            return updateFinder.findUpdates(metadata.getArtifacts());
        }
    }

    protected void applyUpdates() throws ProvisioningException {
        GalleonUtils.executeGalleon(options -> provMgr.provision(provMgr.getProvisioningConfig(), options),
                mavenSessionManager.getProvisioningRepo().toAbsolutePath());

        metadata.setChannel(maven.resolvedChannel());
    }

}
