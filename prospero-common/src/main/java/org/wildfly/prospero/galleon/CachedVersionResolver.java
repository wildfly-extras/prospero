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
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.util.HashUtils;
import org.jboss.logging.Logger;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.ChannelMetadataCoordinate;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.spi.MavenVersionsResolver;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class CachedVersionResolver implements MavenVersionsResolver {

    private final MavenVersionsResolver mavenVersionsResolver;
    private final Map<String, Path> paths = new HashMap<>();
    private final Map<String, String> hashes = new HashMap<>();
    private final RepositorySystem system;
    private final DefaultRepositorySystemSession session;

    private final Logger log = Logger.getLogger(CachedVersionResolver.class);

    public CachedVersionResolver(MavenVersionsResolver mavenVersionsResolver, Path installDir, RepositorySystem system, DefaultRepositorySystemSession session) {
        this.mavenVersionsResolver = mavenVersionsResolver;
        this.system = system;
        this.session = session;
        Path artifactLog = installDir.resolve(".installation").resolve(".cache").resolve("artifacts.txt");

        if (Files.exists(artifactLog)) {
            try {
                final List<String> lines = Files.readAllLines(artifactLog);
                for (String line : lines) {
                    String gav = line.split("::")[0];
                    String hash = line.split("::")[1];
                    Path path = Paths.get(line.split("::")[2]);
                    final MavenArtifact mavenArtifact = MavenArtifact.fromString(gav);
                    final String key = asKey(mavenArtifact.getGroupId(), mavenArtifact.getArtifactId(), mavenArtifact.getVersion(), mavenArtifact.getClassifier(), mavenArtifact.getExtension());
                    paths.put(key, installDir.resolve(path));
                    hashes.put(key, hash);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (MavenUniverseException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String asKey(String groupId, String artifactId, String version, String classifier, String extension) {
        return String.format("%s:%s:%s:%s:%s", groupId, artifactId, version, classifier, extension);
    }

    @Override
    public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier) {
        return mavenVersionsResolver.getAllVersions(groupId, artifactId, extension, classifier);
    }

    @Override
    public File resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException {
        Optional<File> path = resolveFromInstallation(groupId, artifactId, extension, classifier, version);
        if (path.isPresent()) {
            return path.get();
        } else {
            return mavenVersionsResolver.resolveArtifact(groupId, artifactId, extension, classifier, version);
        }
    }

    private Optional<File> resolveFromInstallation(String groupId, String artifactId, String extension, String classifier, String version) {
        final String key = asKey(groupId, artifactId, version, classifier, extension);
        if (paths.containsKey(key)) {
            final Path path = paths.get(key);
            try {
                final String hash = HashUtils.hashFile(path);
                if (!hash.equals(hashes.get(key))) {
                    log.debug("Hashes don't match for " + key);
                    return Optional.empty();
                }
                final InstallRequest request = new InstallRequest();
                request.setArtifacts(List.of(new DefaultArtifact(groupId, artifactId, classifier, extension, version, null, path.toFile())));
                system.install(session, request);
                return Optional.of(path.toFile());
            } catch (InstallationException | IOException e) {
                log.debug("Unable to use cached artifact " + key, e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Override
    public List<File> resolveArtifacts(List<ArtifactCoordinate> coordinates) throws UnresolvedMavenArtifactException {
        return mavenVersionsResolver.resolveArtifacts(coordinates);
    }

    @Override
    public List<URL> resolveChannelMetadata(List<? extends ChannelMetadataCoordinate> manifestCoords) throws UnresolvedMavenArtifactException {
        return mavenVersionsResolver.resolveChannelMetadata(manifestCoords);
    }
}
