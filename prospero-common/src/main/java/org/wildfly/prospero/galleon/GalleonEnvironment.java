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
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelMetadataCoordinate;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.InvalidChannelMetadataException;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.exceptions.ChannelDefinitionException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.UnresolvedChannelMetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GalleonEnvironment implements AutoCloseable {

    public static final String TRACK_JBMODULES = "JBMODULES";
    public static final String TRACK_JBEXAMPLES = "JBEXTRACONFIGS";
    public static final String TRACK_JB_ARTIFACTS_RESOLVE = "JB_ARTIFACTS_RESOLVE";

    public static final String TRACK_RESOLVING_VERSIONS = "RESOLVING_VERSIONS";
    private final ProvisioningManager provisioningManager;
    private final MavenRepoManager repositoryManager;
    private final ChannelSession channelSession;
    private final List<Channel> channels;

    private GalleonEnvironment(Builder builder) throws ProvisioningException, MetadataException, ChannelDefinitionException, UnresolvedChannelMetadataException {
        Optional<Console> console = Optional.ofNullable(builder.console);
        Optional<ChannelManifest> restoreManifest = Optional.ofNullable(builder.manifest);
        channels = builder.channels;
        List<Channel> substitutedChannels = new ArrayList<>();
        final ChannelManifestSubstitutor substitutor = new ChannelManifestSubstitutor(Map.of("installation.home", builder.installDir.toString()));
        // substitute any properties found in URL of ChannelManifestCoordinate.
        for (Channel channel : channels) {
            substitutedChannels.add(substitutor.substitute(channel));
        }

        final RepositorySystem system = builder.mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = builder.mavenSessionManager.newRepositorySystemSession(system);
        final Path sourceServerPath = builder.sourceServerPath == null? builder.installDir:builder.sourceServerPath;
        MavenVersionsResolver.Factory factory;
        try {
            factory = new CachedVersionResolverFactory(new VersionResolverFactory(system, session), sourceServerPath, system, session);
        } catch (IOException e) {
            ProsperoLogger.ROOT_LOGGER.debug("Unable to read artifact cache, falling back to Maven resolver.", e);
            factory = new VersionResolverFactory(system, session);
        }
        channelSession = initChannelSession(session, factory);

        if (builder.artifactDirectResolve) {
            repositoryManager = new MavenArtifactDirectResolverRepositoryManager(channelSession);
        } else {
            if (restoreManifest.isEmpty()) {
                repositoryManager = new ChannelMavenArtifactRepositoryManager(channelSession);
            } else {
                repositoryManager = new ChannelMavenArtifactRepositoryManager(channelSession, restoreManifest.get());
            }
        }
        provisioningManager = GalleonUtils.getProvisioningManager(builder.installDir, repositoryManager, builder.fpTracker);

        final ProvisioningLayoutFactory layoutFactory = provisioningManager.getLayoutFactory();
        Stream.of(ProvisioningLayoutFactory.TRACK_LAYOUT_BUILD,
                  ProvisioningLayoutFactory.TRACK_PACKAGES,
                  ProvisioningLayoutFactory.TRACK_CONFIGS,
                  TRACK_JBMODULES,
                  TRACK_JBEXAMPLES)
                .forEach(t->layoutFactory.setProgressCallback(t, new GalleonCallbackAdapter(console.orElse(null), t)));

        final DownloadsCallbackAdapter callback = new DownloadsCallbackAdapter(console.orElse(null));
        session.setTransferListener(callback);
        layoutFactory.setProgressCallback(TRACK_JB_ARTIFACTS_RESOLVE, callback);
    }

    private ChannelSession initChannelSession(DefaultRepositorySystemSession session, MavenVersionsResolver.Factory factory) throws UnresolvedChannelMetadataException, ChannelDefinitionException {
        final ChannelSession channelSession;
        try {
            channelSession = new ChannelSession(channels, factory);
        } catch (UnresolvedMavenArtifactException e) {
            final Set<ChannelMetadataCoordinate> missingArtifacts = e.getUnresolvedArtifacts().stream()
                    .map(a -> new ChannelMetadataCoordinate(a.getGroupId(), a.getArtifactId(), a.getVersion(), a.getClassifier(), a.getExtension()))
                    .collect(Collectors.toSet());

            throw new UnresolvedChannelMetadataException(ProsperoLogger.ROOT_LOGGER.unableToResolve(), e, missingArtifacts,
                    e.getAttemptedRepositories(), session.isOffline());
        } catch (InvalidChannelMetadataException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                final String url = e.getValidationMessages().get(0);
                try {
                    final ChannelMetadataCoordinate coord = new ChannelMetadataCoordinate(new URL(url));
                    throw new UnresolvedChannelMetadataException(ProsperoLogger.ROOT_LOGGER.unableToResolve(), e, Set.of(coord), Collections.emptySet(), session.isOffline());
                } catch (MalformedURLException ex) {
                    throw ProsperoLogger.ROOT_LOGGER.invalidManifest(e);
                }
            }
            throw ProsperoLogger.ROOT_LOGGER.invalidManifest(e);
        }
        return channelSession;
    }

    public ProvisioningManager getProvisioningManager() {
        return provisioningManager;
    }

    public ChannelSession getChannelSession() {
        return channelSession;
    }

    public MavenRepoManager getRepositoryManager() {
        return repositoryManager;
    }

    public List<Channel> getChannels() {
        return channels;
    }

    @Override
    public void close() {
        provisioningManager.close();
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
        private Consumer<String> fpTracker;
        private Path sourceServerPath;
        private boolean artifactDirectResolve;

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

        public Builder setResolvedFpTracker(Consumer<String> fpTracker) {
            this.fpTracker = fpTracker;
            return this;
        }

        public GalleonEnvironment build() throws ProvisioningException, OperationException {
            return new GalleonEnvironment(this);
        }

        public Builder setSourceServerPath(Path sourceServerPath) {
            this.sourceServerPath = sourceServerPath;
            return this;
        }

        /**
         * Resolving the artifacts directly without checking the channel manifest or not.
         *
         * @param artifactDirectResolve true if resolving directly, false otherwise.
         * @return this for fluent api
         */
        public Builder setArtifactDirectResolve(boolean artifactDirectResolve) {
            this.artifactDirectResolve = artifactDirectResolve;
            return this;
        }

        public Path getSourceServerPath() {
            return sourceServerPath;
        }
    }
}
