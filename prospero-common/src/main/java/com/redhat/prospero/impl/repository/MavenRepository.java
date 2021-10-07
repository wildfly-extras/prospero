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

package com.redhat.prospero.impl.repository;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Repository;
import com.redhat.prospero.api.Resolver;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.jboss.galleon.universe.maven.MavenUniverseException;

public class MavenRepository implements Repository {

    protected final Resolver resolver;
    private final boolean strict;

    public MavenRepository(RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession, List<Channel> channels) {
        this.resolver = new DefaultResolver(repositoriesFromChannels(channels), repositorySystem, repositorySystemSession);
        this.strict = false;
    }

    public MavenRepository(Resolver resolver, boolean strict) {
        this.resolver = resolver;
        this.strict = strict;
    }

    @Override
    public File resolve(Artifact artifact) throws ArtifactNotFoundException {
        try {
            final ArtifactResult result = resolver.resolve(artifact);

            if (!result.isResolved()) {
                throw new ArtifactNotFoundException("Failed to resolve " + artifact);
            }
            if (result.isMissing()) {
                throw new ArtifactNotFoundException("Repository is missing artifact " + artifact);
            }
            return result.getArtifact().getFile();
        } catch (ArtifactResolutionException e) {
            throw new ArtifactNotFoundException("Unable to find artifact [" + artifact + "]", e);
        }
    }

    @Override
    public Artifact resolveLatestVersionOf(Artifact artifact) throws ArtifactNotFoundException {
        final DefaultArtifact artifact1 = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(),
                artifact.getClassifier(), artifact.getExtension(), strict? artifact.getVersion() : "[" + artifact.getVersion() + ",)");

        try {
            final VersionRangeResult versionRangeResult = resolver.getVersionRange(artifact1);
            final Version highestVersion = getHighestVersion(versionRangeResult, artifact);
            if (highestVersion == null) {
                return null;
            } else {
                final DefaultArtifact highest = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(),
                        artifact.getClassifier(), artifact.getExtension(), highestVersion.toString());
                return highest.setFile(resolve(highest));
            }
        } catch (VersionRangeResolutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected Version getHighestVersion(VersionRangeResult versionRangeResult, Artifact artifact) throws ArtifactNotFoundException {
        return versionRangeResult.getHighestVersion();
    }

    @Override
    public VersionRangeResult getVersionRange(Artifact artifact) throws MavenUniverseException {
        try {
            return resolver.getVersionRange(artifact);
        } catch (VersionRangeResolutionException ex) {
            throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
        }
    }

    public List<RemoteRepository> repositoriesFromChannels(List<Channel> channels) {
        return channels.stream().map(c -> newRepository(c.getName(), c.getUrl())).collect(Collectors.toList());
    }

    private RemoteRepository newRepository(String channel, String url) {
        return new RemoteRepository.Builder(channel, "default", url).build();
    }

}
