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

import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.spi.ChannelResolvable;
import org.wildfly.channel.ArtifactCoordinate;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.Stream;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.prospero.Messages;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class ChannelMavenArtifactRepositoryManager implements MavenRepoManager, ChannelResolvable {
    private final ChannelSession channelSession;
    private final ChannelManifest manifest;

    public ChannelMavenArtifactRepositoryManager(ChannelSession channelSession) {
        this.channelSession = channelSession;
        this.manifest = null;
    }

    public ChannelMavenArtifactRepositoryManager(ChannelSession channelSession, ChannelManifest manifest) {
        this.channelSession = channelSession;
        this.manifest = manifest;
    }

    @Override
    public void resolve(MavenArtifact artifact) throws MavenUniverseException {
        try {
            final org.wildfly.channel.MavenArtifact result;
            if (manifest == null) {
                result = channelSession.resolveMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                        artifact.getClassifier(), null);
            } else {
                Optional<DefaultArtifact> found = manifest.findStreamFor(artifact.getGroupId(), artifact.getArtifactId()).map(this::streamToArtifact);

                if (found.isPresent()) {
                    result = channelSession.resolveDirectMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                            artifact.getClassifier(), found.get().getVersion());
                } else if (isUniverseOrProducerArtifact(artifact.getArtifactId())) {
                    result = channelSession.resolveMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                            artifact.getClassifier(), null);
                } else {
                    throw Messages.MESSAGES.unableToResolve(artifact.getCoordsAsString());
                }
            }

            MavenArtifactMapper.resolve(artifact, result);
        } catch (UnresolvedMavenArtifactException e) {
            throw new MavenUniverseException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public void resolveAll(Collection<MavenArtifact> artifacts) throws MavenUniverseException {
        final MavenArtifactMapper mapper = new MavenArtifactMapper(artifacts);
        if (manifest == null) {
            final List<org.wildfly.channel.MavenArtifact> channelArtifacts = channelSession.resolveMavenArtifacts(mapper.toChannelArtifacts());

            mapper.applyResolution(channelArtifacts);
        } else {

            List<ArtifactCoordinate> coordinates = toResolvableCoordinates(mapper.toChannelArtifacts());

            final List<org.wildfly.channel.MavenArtifact> channelArtifacts = channelSession.resolveDirectMavenArtifacts(coordinates);

            mapper.applyResolution(channelArtifacts);

            for (MavenArtifact artifact : artifacts) {
                // workaround for when provisioning doesn't add universe artifacts
                if (artifact.getPath() == null) {
                    if (isUniverseOrProducerArtifact(artifact.getArtifactId())) {
                        org.wildfly.channel.MavenArtifact result = channelSession.resolveMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                                artifact.getClassifier(), null);

                        MavenArtifactMapper.resolve(artifact, result);
                    } else {
                        throw Messages.MESSAGES.unableToResolve(artifact.getCoordsAsString());
                    }
                }
            }
        }
    }

    private List<ArtifactCoordinate> toResolvableCoordinates(List<ArtifactCoordinate> artifactCoordinates) throws MavenUniverseException {
        List<ArtifactCoordinate> coordinates = new ArrayList<>();

        for (ArtifactCoordinate coord : artifactCoordinates) {
            Optional<DefaultArtifact> found = manifest.findStreamFor(coord.getGroupId(), coord.getArtifactId()).map(this::streamToArtifact);
            if (found.isPresent()) {
                coordinates.add(new ArtifactCoordinate(
                        coord.getGroupId(),
                        coord.getArtifactId(),
                        coord.getExtension(),
                        coord.getClassifier(),
                        found.get().getVersion()
                ));
            } else {
                throw Messages.MESSAGES.unableToResolve(coord.getGroupId()+ ":" + coord.getGroupId()+":"+coord.getExtension());
            }
        }

        return coordinates;
    }

    private boolean isUniverseOrProducerArtifact(String artifactId) {
        return artifactId.equals("community-universe") || artifactId.equals("wildfly-producers");
    }

    private DefaultArtifact streamToArtifact(Stream s) {
        return new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion());
    }

    @Override
    public boolean isResolved(MavenArtifact artifact) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public boolean isLatestVersionResolved(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact, String lowestQualifier, Pattern includeVersion, Pattern excludeVersion) throws MavenUniverseException {
        resolve(artifact);
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact, String lowestQualifier, boolean locallyAvailable) throws MavenUniverseException {
        // TODO: handle version ranges
        resolve(artifact);
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact) throws MavenUniverseException {
        return getLatestVersion(artifact, null, null, null);
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier) throws MavenUniverseException {
        return getLatestVersion(artifact, lowestQualifier, null, null);
    }

    @Override
    public String getLatestVersion(MavenArtifact artifact, String lowestQualifier, Pattern includeVersion, Pattern excludeVersion) throws MavenUniverseException {
        // TODO: handle version ranges
        try {
            return channelSession.resolveMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                    artifact.getClassifier(), null).getVersion();
        } catch (UnresolvedMavenArtifactException e) {
            throw new MavenUniverseException(e.getMessage(), e);
        }
    }

    @Override
    public List<String> getAllVersions(MavenArtifact artifact) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public List<String> getAllVersions(MavenArtifact artifact, Pattern includeVersion, Pattern excludeVersion) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void install(MavenArtifact artifact, Path path) throws MavenUniverseException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public ChannelManifest resolvedChannel() {
        return channelSession.getRecordedChannel();
    }
}
