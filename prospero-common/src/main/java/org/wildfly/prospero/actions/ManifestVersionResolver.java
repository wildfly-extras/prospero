/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.actions;

import org.apache.commons.io.IOUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.jboss.galleon.util.HashUtils;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.version.VersionMatcher;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.model.ManifestVersionRecord;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Identifies manifests used to provision installation.
 *
 * In case of Maven-based manifests, the identifier is the full GAV;
 * for URL-based manifests it's the URL and a hash of the manifest content;
 * for open manifests it's list of repositories + strategy.
 */
class ManifestVersionResolver {

    private final VersionResolverFactory resolverFactory;

    ManifestVersionResolver(MavenSessionManager msm) {
        final MavenSessionManager mavenSessionManager = new MavenSessionManager(msm);
        mavenSessionManager.setOffline(true);
        final RepositorySystem system = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = mavenSessionManager.newRepositorySystemSession(system);
        resolverFactory = new VersionResolverFactory(system, session);
    }

    ManifestVersionResolver(VersionResolverFactory resolverFactory) {
        this.resolverFactory = resolverFactory;
    }

    ManifestVersionRecord getCurrentVersions(List<Channel> channels) throws MetadataException {
        Objects.requireNonNull(channels);

        final ManifestVersionRecord manifestVersionRecord = new ManifestVersionRecord();
        for (Channel channel : channels) {
            final ChannelManifestCoordinate manifestCoordinate = channel.getManifestCoordinate();
            if (manifestCoordinate == null) {
                final List<String> repos = channel.getRepositories().stream().map(Repository::getId).collect(Collectors.toList());
                manifestVersionRecord.addManifest(new ManifestVersionRecord.NoManifest(repos, channel.getNoStreamStrategy().toString()));
            } else if (manifestCoordinate.getUrl() != null) {
                try {
                    final String content = IOUtils.toString(manifestCoordinate.getUrl());
                    final String hashCode = HashUtils.hash(content);
                    manifestVersionRecord.addManifest(new ManifestVersionRecord.UrlManifest(manifestCoordinate.getUrl().toExternalForm(), hashCode));
                } catch (IOException e) {
                    throw Messages.MESSAGES.unableToDownloadFile(manifestCoordinate.getUrl(), e);
                }
            } else if (manifestCoordinate.getVersion() != null) {
                manifestVersionRecord.addManifest(new ManifestVersionRecord.MavenManifest(manifestCoordinate.getGroupId(), manifestCoordinate.getArtifactId(), manifestCoordinate.getVersion()));
            } else {
                final MavenVersionsResolver mavenVersionsResolver = resolverFactory.create(channel.getRepositories());
                final Optional<String> latestVersion = VersionMatcher.getLatestVersion(mavenVersionsResolver.getAllVersions(manifestCoordinate.getGroupId(), manifestCoordinate.getArtifactId(),
                        manifestCoordinate.getExtension(), manifestCoordinate.getClassifier()));
                manifestVersionRecord.addManifest(new ManifestVersionRecord.MavenManifest(manifestCoordinate.getGroupId(), manifestCoordinate.getArtifactId(), latestVersion.get()));
            }
        }
        return manifestVersionRecord;
    }
}
