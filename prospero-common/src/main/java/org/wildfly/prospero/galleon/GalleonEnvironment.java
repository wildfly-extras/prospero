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

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class GalleonEnvironment {

    private final ProvisioningManager provisioningManager;
    private final ChannelMavenArtifactRepositoryManager repositoryManager;
    private final ChannelSession channelSession;
    private final List<Channel> channels;

    private GalleonEnvironment(Builder builder) throws ProvisioningException {
        Optional<Console> console = Optional.ofNullable(builder.console);
        Optional<ChannelManifest> restoreManifest = Optional.ofNullable(builder.manifest);
        channels = builder.channels;

        final RepositorySystem system = builder.mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = builder.mavenSessionManager.newRepositorySystemSession(system);
        final VersionResolverFactory factory = new VersionResolverFactory(system, session);
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

    public ProvisioningManager getProvisioningManager() {
        return provisioningManager;
    }

    public ChannelMavenArtifactRepositoryManager getRepositoryManager() {
        return repositoryManager;
    }

    public ChannelSession getChannelSession() {
        return channelSession;
    }

    public List<Channel> getChannels() {
        return channels;
    }

    public static Builder builder(Path installDir, List<Channel> channels, MavenSessionManager mavenSessionManager) {
        Objects.requireNonNull(installDir);
        Objects.requireNonNull(channels);
        Objects.requireNonNull(mavenSessionManager);

        return new Builder(installDir, channels, mavenSessionManager);
    }

    public static class Builder {

        private final Path installDir;
        private final List<Channel> channels;
        private final MavenSessionManager mavenSessionManager;
        private Console console;
        private ChannelManifest manifest;

        private Builder(Path installDir, List<Channel> channels, MavenSessionManager mavenSessionManager) {
            this.installDir = installDir;
            this.channels = channels;
            this.mavenSessionManager = mavenSessionManager;
        }

        public Builder setConsole(Console console) {
            this.console = console;
            return this;
        }

        public Builder setRestoreManifest(ChannelManifest manifest) {
            this.manifest = manifest;
            return this;
        }

        public GalleonEnvironment build() throws ProvisioningException, OperationException {
            return new GalleonEnvironment(this);
        }
    }
}
