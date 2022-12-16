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

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.jboss.logging.Logger;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.ChannelMetadataCoordinate;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.spi.MavenVersionsResolver;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Attempts to resolve artifact from local installation cache first. If that's not possible
 * falls back onto {@code fallback} {@code MavenVersionsResolver}.
 *
 * Installs locally resolved artifacts in LRM to allow galleon to start thin servers.
 */
public class CachedVersionResolver implements MavenVersionsResolver {
    private final MavenVersionsResolver fallbackResolver;
    private final RepositorySystem system;
    private final RepositorySystemSession session;
    private final ArtifactCache artifactCache;

    private final Logger log = Logger.getLogger(CachedVersionResolver.class);

    public CachedVersionResolver(MavenVersionsResolver fallbackResolver, ArtifactCache cache, RepositorySystem system, RepositorySystemSession session) {
        this.fallbackResolver = fallbackResolver;
        this.system = system;
        this.session = session;
        this.artifactCache = cache;
    }

    @Override
    public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier) {
        return fallbackResolver.getAllVersions(groupId, artifactId, extension, classifier);
    }

    @Override
    public File resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException {
        Optional<File> path = artifactCache.getArtifact(groupId, artifactId, extension, classifier, version);
        if (path.isEmpty()) {
            return fallbackResolver.resolveArtifact(groupId, artifactId, extension, classifier, version);
        } else {
            // we need to install the artifact locally so that galleon can start embedded server to generate configurations
            if (installArtifactLocally(groupId, artifactId, extension, classifier, version, path.get())) {
                return path.get();
            } else {
                return fallbackResolver.resolveArtifact(groupId, artifactId, extension, classifier, version);
            }
        }
    }

    @Override
    public List<File> resolveArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException {
        final List<Function<List<File>, File>> res = new ArrayList<>(coordinates.size());
        final List<ArtifactCoordinate> missingArtifacts = new ArrayList<>();
        int index = 0;
        for (ArtifactCoordinate coordinate : coordinates) {
            Optional<File> path = artifactCache.getArtifact(coordinate.getGroupId(), coordinate.getArtifactId(),
                    coordinate.getExtension(), coordinate.getClassifier(), coordinate.getVersion());
            if (path.isEmpty()) {
                int i = index++;
                res.add((list)->list.get(i));
                missingArtifacts.add(coordinate);
            } else {
                // we need to install the artifact locally so that galleon can start embedded server to generate configurations
                if (installArtifactLocally(coordinate.getGroupId(), coordinate.getArtifactId(),
                        coordinate.getExtension(), coordinate.getClassifier(), coordinate.getVersion(), path.get())) {
                    res.add((list) -> path.get());
                } else {
                    int i = index++;
                    res.add((list)->list.get(i));
                    missingArtifacts.add(coordinate);
                }
            }
        }

        final List<File> resolvedFromMaven = fallbackResolver.resolveArtifacts(missingArtifacts);

        return res.stream().map(f->f.apply(resolvedFromMaven)).collect(Collectors.toList());
    }

    @Override
    public List<URL> resolveChannelMetadata(List<? extends ChannelMetadataCoordinate> manifestCoords) throws UnresolvedMavenArtifactException {
        return fallbackResolver.resolveChannelMetadata(manifestCoords);
    }

    private boolean installArtifactLocally(String groupId, String artifactId, String extension, String classifier, String version, File path) {
        try {
            final InstallRequest request = new InstallRequest();
            request.setArtifacts(List.of(new DefaultArtifact(groupId, artifactId, classifier, extension, version, null, path)));
            system.install(session, request);
            return true;
        } catch (InstallationException e) {
            log.debug("Unable to install cached artifact into LRM, falling back to resolver.", e);
            return false;
        }
    }
}