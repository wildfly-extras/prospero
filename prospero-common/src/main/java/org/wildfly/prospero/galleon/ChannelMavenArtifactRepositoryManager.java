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

import org.wildfly.channel.spi.ChannelResolvable;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.Stream;
import org.wildfly.channel.UnresolvedMavenArtifactException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class ChannelMavenArtifactRepositoryManager implements MavenRepoManager, ChannelResolvable {
    private final ChannelSession channelSession;
    private final Channel manifest;

    public ChannelMavenArtifactRepositoryManager(ChannelSession channelSession) {
        this.channelSession = channelSession;
        this.manifest = null;
    }

    public ChannelMavenArtifactRepositoryManager(ChannelSession channelSession, Channel manifest) {
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
                } else if (artifact.getArtifactId().equals("community-universe") || artifact.getArtifactId().equals("wildfly-producers")) {
                    result = channelSession.resolveMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                            artifact.getClassifier(), null);
                } else {
                    throw new MavenUniverseException("Unable to resolve " + artifact);
                }
            }
            artifact.setVersion(result.getVersion());
            artifact.setPath(result.getFile().toPath());
        } catch (UnresolvedMavenArtifactException e) {
            throw new MavenUniverseException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public void resolveAll(Collection<MavenArtifact> artifacts) throws MavenUniverseException {
        if (manifest == null) {
            final MavenArtifactMapper mapper = new MavenArtifactMapper(artifacts);

            final List<org.wildfly.channel.MavenArtifact> channelArtifacts = channelSession.resolveMavenArtifacts(mapper.toChannelArtifacts());

            mapper.applyResolution(channelArtifacts);
        } else {
            // TODO: add support for bulk ops
            throw new UnsupportedOperationException("Not implemented yet");
        }


    }

    private DefaultArtifact streamToArtifact(Stream s) {
        return new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion());
    }

    private void resolveSynchronously(List<MavenArtifact> artifacts) throws MavenUniverseException {
        for (MavenArtifact artifact : artifacts) {
            resolve(artifact);
        }
    }

    private void resolveConcurrently(List<MavenArtifact> artifacts) throws MavenUniverseException {
        final ExecutorService executorService = Executors.newWorkStealingPool(30);
        try {
            List<CompletableFuture<Void>> allPackages = new ArrayList<>();

            for (MavenArtifact artifact : artifacts) {
                final CompletableFuture<Void> cf = new CompletableFuture<>();
                allPackages.add(cf);
                executorService.submit(() -> {
                    try {
                        resolve(artifact);
                        cf.complete(null);
                    } catch (Exception e) {
                        cf.completeExceptionally(e);
                    }
                });
            }

            CompletableFuture.allOf(allPackages.toArray(new CompletableFuture[]{})).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof MavenUniverseException) {
                throw (MavenUniverseException) e.getCause();
            } else {
                throw e;
            }
        } finally {
            executorService.shutdown();
        }
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

    public Channel resolvedChannel() {
        return channelSession.getRecordedChannel();
    }
}
