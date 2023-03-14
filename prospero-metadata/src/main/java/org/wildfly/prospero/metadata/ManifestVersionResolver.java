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

package org.wildfly.prospero.metadata;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;
import org.jboss.galleon.util.HashUtils;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.MavenVersionsResolver;
import org.wildfly.channel.version.VersionMatcher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Path;
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
public class ManifestVersionResolver {

    private final VersionResolverFactory resolverFactory;

    /**
     * creates the resolver using an offline repository session
     *
     * @param localMavenCache - path to the local cache used to resolve metadata during provisioning
     * @param system
     */
    public ManifestVersionResolver(Path localMavenCache, RepositorySystem system) {
        Objects.requireNonNull(localMavenCache);
        Objects.requireNonNull(system);

        final DefaultRepositorySystemSession session = newRepositorySystemSession(system, localMavenCache);
        this.resolverFactory = new VersionResolverFactory(system, session);
    }

    // used in tests only
    ManifestVersionResolver(VersionResolverFactory resolverFactory) {
        this.resolverFactory = resolverFactory;
    }

    /**
     * Resolves the latest versions of manifests used in channels
     *
     * @param channels - list of provisioned channels
     * @return {@code ManifestVersionRecord} of latest available versions of manifests
     * @throws IOException - if unable to resolve any of the manifests
     */
    public ManifestVersionRecord getCurrentVersions(List<Channel> channels) throws IOException {
        Objects.requireNonNull(channels);

        final ManifestVersionRecord manifestVersionRecord = new ManifestVersionRecord();
        for (Channel channel : channels) {
            final ChannelManifestCoordinate manifestCoordinate = channel.getManifestCoordinate();
            if (manifestCoordinate == null) {
                final List<String> repos = channel.getRepositories().stream().map(Repository::getId).collect(Collectors.toList());
                manifestVersionRecord.addManifest(new ManifestVersionRecord.NoManifest(repos, channel.getNoStreamStrategy().toString()));
            } else if (manifestCoordinate.getUrl() != null) {
                final String content = read(manifestCoordinate.getUrl());
                final String hashCode = HashUtils.hash(content);
                manifestVersionRecord.addManifest(new ManifestVersionRecord.UrlManifest(manifestCoordinate.getUrl().toExternalForm(), hashCode));
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

    private String read(URL url) throws IOException {
        final InputStream inputStream = url.openStream();
        final OutputStream outputStream = new ByteArrayOutputStream();
        inputStream.transferTo(outputStream);
        return outputStream.toString();
    }

    private static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system,
                                                                             Path localMavenCache) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(localMavenCache.toFile());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        session.setOffline(true);
        return session;
    }
}
