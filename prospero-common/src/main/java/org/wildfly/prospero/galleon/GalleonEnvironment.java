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

package org.wildfly.prospero.galleon;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.wfchannel.ChannelRefUpdater;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class GalleonEnvironment {

    private final ProvisioningManager provisioningManager;
    private final ChannelMavenArtifactRepositoryManager repositoryManager;
    private final ChannelSession channelSession;
    private final List<ChannelRef> effectiveChannelRefs;

    private GalleonEnvironment(Builder builder) throws ProvisioningException, OperationException {
        Optional<Console> console = Optional.ofNullable(builder.console);
        Optional<Channel> restoreManifest = Optional.ofNullable(builder.manifest);

        if (builder.skipUpdateChannel) {
            effectiveChannelRefs = builder.prosperoConfig.getChannels();
        } else {
            effectiveChannelRefs = new ChannelRefUpdater(builder.mavenSessionManager)
                    .resolveLatest(builder.prosperoConfig.getChannels(), builder.prosperoConfig.getRemoteRepositories());
        }
        final List<Channel> channels = mapToChannels(effectiveChannelRefs);

        final RepositorySystem system = builder.mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = builder.mavenSessionManager.newRepositorySystemSession(system);
        final VersionResolverFactory factory = new VersionResolverFactory(system, session, builder.prosperoConfig.getRemoteRepositories());
        channelSession = new ChannelSession(channels, factory);
        if (restoreManifest.isEmpty()) {
            repositoryManager = new ChannelMavenArtifactRepositoryManager(channelSession);
        } else {
            repositoryManager = new ChannelMavenArtifactRepositoryManager(channelSession, restoreManifest.get());
        }
        provisioningManager = GalleonUtils.getProvisioningManager(builder.installDir, repositoryManager);

        final ProvisioningLayoutFactory layoutFactory = provisioningManager.getLayoutFactory();
        if (console.isPresent()) {
            layoutFactory.setProgressCallback("LAYOUT_BUILD", console.get().getProgressCallback("LAYOUT_BUILD"));
            layoutFactory.setProgressCallback("PACKAGES", console.get().getProgressCallback("PACKAGES"));
            layoutFactory.setProgressCallback("CONFIGS", console.get().getProgressCallback("CONFIGS"));
            layoutFactory.setProgressCallback("JBMODULES", console.get().getProgressCallback("JBMODULES"));
        }
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

    public ProvisioningManager getProvisioningManager() {
        return provisioningManager;
    }

    public ChannelMavenArtifactRepositoryManager getRepositoryManager() {
        return repositoryManager;
    }

    public ChannelSession getChannelSession() {
        return channelSession;
    }

    public List<ChannelRef> getUpdatedRefs() {
        return effectiveChannelRefs;
    }

    public static Builder builder(Path installDir, ProsperoConfig prosperoConfig, MavenSessionManager mavenSessionManager) {
        Objects.requireNonNull(installDir);
        Objects.requireNonNull(prosperoConfig);
        Objects.requireNonNull(mavenSessionManager);

        return new Builder(installDir, prosperoConfig, mavenSessionManager);
    }

    public static class Builder {

        private final Path installDir;
        private final ProsperoConfig prosperoConfig;
        private final MavenSessionManager mavenSessionManager;
        private Console console;
        private Channel manifest;
        private boolean skipUpdateChannel;

        private Builder(Path installDir, ProsperoConfig prosperoConfig, MavenSessionManager mavenSessionManager) {
            this.installDir = installDir;
            this.prosperoConfig = prosperoConfig;
            this.mavenSessionManager = mavenSessionManager;
        }

        public Builder setConsole(Console console) {
            this.console = console;
            return this;
        }

        public Builder setRestoreManifest(Channel manifest) {
            this.manifest = manifest;
            return this;
        }

        public Builder skipUpdateChannel(boolean skipUpdateChannel) {
            this.skipUpdateChannel = skipUpdateChannel;
            return this;
        }

        public GalleonEnvironment build() throws ProvisioningException, OperationException {
            return new GalleonEnvironment(this);
        }
    }
}
