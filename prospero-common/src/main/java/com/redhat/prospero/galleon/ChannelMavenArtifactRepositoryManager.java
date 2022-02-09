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

package com.redhat.prospero.galleon;

import com.redhat.prospero.api.Manifest;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelSession;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.spi.MavenVersionsResolver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class ChannelMavenArtifactRepositoryManager implements MavenRepoManager {
    private ChannelSession channelSession;
    private Set<MavenArtifact> resolvedArtifacts = new HashSet<>();
    private Manifest manifest = null;

    public ChannelMavenArtifactRepositoryManager(List<Channel> channels, MavenVersionsResolver.Factory factory) {
        channelSession = new ChannelSession(channels, factory);
    }

    public ChannelMavenArtifactRepositoryManager(ChannelSession channelSession) {
        this.channelSession = channelSession;
    }

    public ChannelMavenArtifactRepositoryManager(List<Channel> channels, MavenVersionsResolver.Factory factory, Manifest manifest) {
        channelSession = new ChannelSession(channels, factory);
        this.manifest = manifest;
    }

    @Override
    public void resolve(MavenArtifact artifact) throws MavenUniverseException {
        try {
            final org.wildfly.channel.MavenArtifact result;
            if (manifest == null) {
                result = channelSession.resolveLatestMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                        artifact.getClassifier(), artifact.getVersion());
            } else {
                final DefaultArtifact gav = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getExtension(), artifact.getVersion() != null ? artifact.getVersion() : artifact.getVersionRange());
                Artifact found = manifest.find(gav);
                if (found != null) {
                    result = channelSession.resolveExactMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                            artifact.getClassifier(), found.getVersion());
                } else if (artifact.getArtifactId().equals("community-universe") || artifact.getArtifactId().equals("wildfly-producers")) {
                    result = channelSession.resolveLatestMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                            artifact.getClassifier(), artifact.getVersion());
                } else {
                    throw new MavenUniverseException("Unable to resolve " + artifact);
                }
            }
            artifact.setVersion(result.getVersion());
            artifact.setPath(result.getFile().toPath());
            resolvedArtifacts.add(artifact);
        } catch (UnresolvedMavenArtifactException e) {
            throw new MavenUniverseException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public void resolveAll(List<MavenArtifact> artifacts) throws MavenUniverseException {
        final ExecutorService executorService = Executors.newWorkStealingPool(30);
        List<CompletableFuture<Void>> allPackages = new ArrayList<>();

        for (MavenArtifact artifact : artifacts) {
            final CompletableFuture<Void> cf = new CompletableFuture<>();
            executorService.submit(()->{
                try {
                    resolve(artifact);
                    cf.complete(null);
                } catch (MavenUniverseException e) {
                    cf.completeExceptionally(e);
                }
            });
            allPackages.add(cf);
        }

        CompletableFuture.allOf(allPackages.toArray(new CompletableFuture[]{})).join();
        executorService.shutdown();
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
            return channelSession.resolveLatestMavenArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                    artifact.getClassifier(), artifact.getVersion()).getVersion();
        } catch (UnresolvedMavenArtifactException e) {
            throw new MavenUniverseException("", e);
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

    public Set<MavenArtifact> resolvedArtfacts() {
        return resolvedArtifacts;
    }
}
