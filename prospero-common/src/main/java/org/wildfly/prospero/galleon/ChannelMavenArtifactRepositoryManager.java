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

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.layout.FeaturePackDescriber;
import org.jboss.galleon.util.ZipUtils;
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
import org.wildfly.prospero.ProsperoLogger;

import java.io.BufferedReader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChannelMavenArtifactRepositoryManager implements MavenRepoManager, ChannelResolvable {
    private static final String REQUIRE_CHANNEL_FOR_ALL_ARTIFACT = "org.wildfly.plugins.galleon.all.artifact.requires.channel.resolution";
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
        org.wildfly.channel.MavenArtifact result;
        if (manifest == null) {
            try {
                result = channelSession.resolveMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                        artifact.getClassifier(), null);
            } catch (UnresolvedMavenArtifactException e) {
                if (requiresChannel(artifact)) {
                    throw new MavenUniverseException(e.getLocalizedMessage(), e);
                } else {
                    // unable to resolve the artifact through the channel.
                    // if the version is defined, let's resolve it directly
                    if (artifact.getVersion() == null) {
                        throw new MavenUniverseException(e.getLocalizedMessage(), e);
                    }
                    try {
                        result = channelSession.resolveDirectMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(), artifact.getVersion());
                    } catch (UnresolvedMavenArtifactException ex) {
                        // if the artifact can not be resolved directly either, we abort
                        throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
                    }
                }
            }
        } else {
            try {
                result = resolveFromPreparedManifest(artifact);
            } catch (UnresolvedMavenArtifactException e) {
                throw new MavenUniverseException(e.getLocalizedMessage(), e);
            }
        }
        MavenArtifactMapper.resolve(artifact, result);
    }

    private boolean fpRequireChannel(MavenArtifact artifact) throws Exception {
        boolean requireChannel = false;
        if (artifact.getVersion() != null && artifact.getExtension() != null && artifact.getExtension().equalsIgnoreCase("zip")) {
            if (artifact.getVersion().equals(artifact.getExtension())) {
                // the requested FPL was in form groupId:artifactId::zip - galleon converts the version wrong
                // TODO: fix in Galleon and change to check if version is null
                return true;
            }

            org.wildfly.channel.MavenArtifact mavenArtifact = channelSession.
                    resolveDirectMavenArtifact(artifact.getGroupId(),
                            artifact.getArtifactId(),
                            artifact.getExtension(),
                            artifact.getClassifier(),
                            artifact.getVersion());
            try {
                FeaturePackDescriber.readSpec(mavenArtifact.getFile().toPath());
            } catch(ProvisioningException ex) {
                // Not a feature-pack
                return requireChannel;
            }
            try (FileSystem fs = ZipUtils.newFileSystem(mavenArtifact.getFile().toPath())) {
                Path resPath = fs.getPath("resources");
                final Path wfRes = resPath.resolve("wildfly");
                final Path channelPropsPath = wfRes.resolve("wildfly-channel.properties");
                if (Files.exists(channelPropsPath)) {
                    Properties props = new Properties();
                    try(BufferedReader reader = Files.newBufferedReader(channelPropsPath)) {
                        props.load(reader);
                    }
                    String resolution = props.getProperty("resolution");
                    if (resolution != null) {
                        requireChannel = "REQUIRED".equals(resolution) || "REQUIRED_FP_ONLY".equals(resolution);
                    }
                }
            }
        }
        return requireChannel;
    }

    private org.wildfly.channel.MavenArtifact resolveFromPreparedManifest(MavenArtifact artifact) throws MavenUniverseException {
        final org.wildfly.channel.MavenArtifact result;
        Optional<DefaultArtifact> found = manifest.findStreamFor(artifact.getGroupId(), artifact.getArtifactId()).map(this::streamToArtifact);

        if (found.isPresent()) {
            result = channelSession.resolveDirectMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                    artifact.getClassifier(), found.get().getVersion());
        } else if (isUniverseOrProducerArtifact(artifact.getArtifactId())) {
            result = channelSession.resolveMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                    artifact.getClassifier(), null);
        } else {
            throw new MavenUniverseException(ProsperoLogger.ROOT_LOGGER.unableToResolve() + " ["+ artifact.getCoordsAsString()+"]");
        }
        return result;
    }

    private boolean requiresChannel(MavenArtifact artifact) {
        boolean requireChannel = Boolean.parseBoolean(artifact.getMetadata().get(REQUIRE_CHANNEL_FOR_ALL_ARTIFACT));
        try {
            if (!requireChannel && ! fpRequireChannel(artifact)) {
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void resolveAll(Collection<MavenArtifact> artifacts) throws MavenUniverseException {

        if (manifest == null) {
            // split the artifacts into requiring channels and not requiring channels
            final List<MavenArtifact> artifactsRequiringChannels = artifacts.stream()
                    .filter(a -> requiresChannel(a))
                    .collect(Collectors.toList());
            final List<MavenArtifact> artifactsNotRequiringChannels = artifacts.stream()
                    .filter(a -> !requiresChannel(a))
                    .collect(Collectors.toList());
            // bulk resolve artifacts requiring channels - if any fail, throw exception
            MavenArtifactMapper mapper = new MavenArtifactMapper(artifactsRequiringChannels);
            List<org.wildfly.channel.MavenArtifact> channelArtifacts = channelSession.resolveMavenArtifacts(mapper.toChannelArtifacts());
            mapper.applyResolution(channelArtifacts);

            // bulk resolve other artifacts
            final MavenArtifactMapper mapperNotRequiringChannels = new MavenArtifactMapper(artifactsNotRequiringChannels);
            resolveArtifactsWithFallbackVersions(mapperNotRequiringChannels, mapperNotRequiringChannels.toChannelArtifacts());
        } else {
            final MavenArtifactMapper mapper = new MavenArtifactMapper(artifacts);
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
                        throw new MavenUniverseException(ProsperoLogger.ROOT_LOGGER.unableToResolve() + " [" +artifact.getCoordsAsString() + "]");
                    }
                }
            }
        }
    }

    private void resolveArtifactsWithFallbackVersions(MavenArtifactMapper mapperNotRequiringChannels, List<ArtifactCoordinate> coordinates) throws MavenUniverseException {
        List<org.wildfly.channel.MavenArtifact> channelArtifacts;
        try {
            channelArtifacts = channelSession.resolveMavenArtifacts(coordinates);
            mapperNotRequiringChannels.applyResolution(channelArtifacts);
        } catch (UnresolvedMavenArtifactException e) {
            if (e.getCause() instanceof ArtifactResolutionException) {
                handleMissingArtifacts(mapperNotRequiringChannels, e);
            } else if (e.getCause() == null) {
                handleMissingStreams(mapperNotRequiringChannels, coordinates, e);
            } else {
                throw new MavenUniverseException(e.getLocalizedMessage(), e);
            }
        }
    }

    private void handleMissingStreams(MavenArtifactMapper mapperNotRequiringChannels, List<ArtifactCoordinate> coordinates, UnresolvedMavenArtifactException e) throws MavenUniverseException {
        final Set<ArtifactCoordinate> unresolvedArtifacts = e.getUnresolvedArtifacts();
        // resolve unresolvedArtifacts directly
        for (ArtifactCoordinate a : unresolvedArtifacts) {
            final List<MavenArtifact> missingArtifacts = mapperNotRequiringChannels.get(
                    new ArtifactCoordinate(a.getGroupId(), a.getArtifactId(),
                            a.getExtension(), a.getClassifier(), a.getVersion()));
            for (MavenArtifact missingArtifact : missingArtifacts) {
                if (missingArtifact.getVersion() == null) {
                    throw new MavenUniverseException(e.getLocalizedMessage(), e);
                }
                final org.wildfly.channel.MavenArtifact mavenArtifact = channelSession.resolveDirectMavenArtifact(missingArtifact.getGroupId(), missingArtifact.getArtifactId(),
                        missingArtifact.getExtension(), missingArtifact.getClassifier(), missingArtifact.getVersion());
                missingArtifact.setPath(mavenArtifact.getFile().toPath());
            }
        }
        // remove unresolvedArtifacts from the list of artifact to resolve
        final List<ArtifactCoordinate> requests = new ArrayList<>();
        for (ArtifactCoordinate a : coordinates) {
            if (!unresolvedArtifacts.contains(new ArtifactCoordinate(a.getGroupId(), a.getArtifactId(), a.getExtension(), a.getClassifier(), ""))) {
                requests.add(a);
            }
        }
        // try resolving the new list, handle missing artifacts (e.g. wrong versions)
        resolveArtifactsWithFallbackVersions(mapperNotRequiringChannels, requests);
    }

    private void handleMissingArtifacts(MavenArtifactMapper mapperNotRequiringChannels, UnresolvedMavenArtifactException e) throws MavenUniverseException {
        List<org.wildfly.channel.MavenArtifact> channelArtifacts;
        final List<ArtifactResult> results = ((ArtifactResolutionException) e.getCause()).getResults();
        channelArtifacts = new ArrayList<>();
        for (ArtifactResult result : results) {
            if (!result.isResolved()) {
                // resolve directly
                final Artifact a = result.getRequest().getArtifact();
                final List<MavenArtifact> missingArtifacts = mapperNotRequiringChannels.get(
                        new ArtifactCoordinate(a.getGroupId(), a.getArtifactId(),
                                a.getExtension(), a.getClassifier(), a.getVersion()));

                for (MavenArtifact missingArtifact : missingArtifacts) {
                    if (missingArtifact.getVersion() == null) {
                        throw new MavenUniverseException(e.getLocalizedMessage(), e);
                    }
                    final org.wildfly.channel.MavenArtifact mavenArtifact = channelSession.resolveDirectMavenArtifact(missingArtifact.getGroupId(), missingArtifact.getArtifactId(),
                            missingArtifact.getExtension(), missingArtifact.getClassifier(), missingArtifact.getVersion());
                    missingArtifact.setPath(mavenArtifact.getFile().toPath());
                }
            } else {
                final Artifact a = result.getArtifact();
                channelArtifacts.add(new org.wildfly.channel.MavenArtifact(a.getGroupId(), a.getArtifactId(),
                        a.getExtension(), a.getClassifier(), a.getVersion(), a.getFile()));
            }
        }
        mapperNotRequiringChannels.applyResolution(channelArtifacts);
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
                throw new MavenUniverseException(ProsperoLogger.ROOT_LOGGER.unableToResolve() +
                        " [" +coord.getGroupId()+ ":" + coord.getGroupId()+":"+coord.getExtension() + "]");
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
