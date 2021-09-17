/*
 * Copyright 2016-2021 Red Hat, Inc. and/or its affiliates
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
package com.redhat.prospero;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Gav;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import static org.eclipse.aether.repository.RepositoryPolicy.CHECKSUM_POLICY_WARN;
import static org.eclipse.aether.repository.RepositoryPolicy.UPDATE_POLICY_NEVER;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.jboss.galleon.universe.maven.MavenUniverseException;

public class MavenResolver {

    private final RepositorySystem repoSystem;
    private final RepositorySystemSession repoSession;
    private final List<RemoteRepository> repositories;
    private final RepositorySystemSession fallbackRepoSession;
    private final List<RemoteRepository> fallbackRepositories;

    public MavenResolver(List<Channel> channels, RepositorySystem repoSystem,
            RepositorySystemSession repoSession, RepositorySystemSession fallbackRepoSession, List<RemoteRepository> fallbackRepositories) {
        this.repoSystem = repoSystem;
        this.repoSession = repoSession;
        this.repositories = newRepositories(channels);
        this.fallbackRepoSession = fallbackRepoSession;
        this.fallbackRepositories = fallbackRepositories;
    }

    public List<RemoteRepository> getRepositories() {
        return repositories;
    }

    public File resolve(Gav artifact) throws ArtifactNotFoundException {
        return doResolve(artifact, repoSession, repositories);
    }

    public File resolveFallback(Gav artifact) throws ArtifactNotFoundException {
        return doResolve(artifact, fallbackRepoSession, fallbackRepositories);
    }

    public File doResolve(Gav artifact, RepositorySystemSession repoSession, List<RemoteRepository> repositories) throws ArtifactNotFoundException {
        if (artifact.getVersion() == null) {
            throw new RuntimeException("Version should be set when resol");
        }
        ArtifactRequest req = new ArtifactRequest();
        req.setArtifact(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(),
                artifact.getClassifier(), artifact.getPackaging(), artifact.getVersion()));
        req.setRepositories(repositories);
        try {
            final ArtifactResult result = repoSystem.resolveArtifact(repoSession, req);
            if (!result.isResolved()) {
                throw new ArtifactNotFoundException("Failed to resolve " + req.getArtifact().toString());
            }
            if (result.isMissing()) {
                throw new ArtifactNotFoundException("Repository is missing artifact " + req.getArtifact().toString());
            }
            return result.getArtifact().getFile();
        } catch (ArtifactResolutionException e) {
            throw new ArtifactNotFoundException("Unable to find artifact [" + artifact + "]", e);
        }
    }

    public Gav findLatestVersionOf(Gav artifact, String range) {
        return findLatest(artifact, range, repoSession, repositories);
    }

    public Gav findLatestFallBack(Gav artifact, String range) {
        return findLatest(artifact, range, fallbackRepoSession, fallbackRepositories);
    }

    private Gav findLatest(Gav artifact, String range, RepositorySystemSession repoSession, List<RemoteRepository> repositories) {
        VersionRangeRequest req = new VersionRangeRequest();

        final DefaultArtifact artifact1 = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getPackaging(), range);
        req.setArtifact(artifact1);
        req.setRepositories(repositories);
        try {
            final VersionRangeResult versionRangeResult = repoSystem.resolveVersionRange(repoSession, req);
            final Version highestVersion = versionRangeResult.getHighestVersion();
            if (highestVersion == null) {
                return null;
            } else {
                System.out.println("FOUND latest version " + highestVersion + " for " + artifact);
                return artifact.newVersion(highestVersion.toString());
            }
        } catch (VersionRangeResolutionException e) {
            throw new RuntimeException(e);
        }
    }

    public VersionRangeResult getVersionRange(Artifact artifact) throws MavenUniverseException {
        return getVersionRange(artifact, repoSession, repositories);
    }

    public VersionRangeResult getVersionRangeFallback(Artifact artifact) throws MavenUniverseException {
        return getVersionRange(artifact, fallbackRepoSession, fallbackRepositories);
    }

    private VersionRangeResult getVersionRange(Artifact artifact, RepositorySystemSession session, List<RemoteRepository> repositories) throws MavenUniverseException {
        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact(artifact);
        rangeRequest.setRepositories(repositories);
        VersionRangeResult rangeResult;
        try {
            rangeResult = repoSystem.resolveVersionRange(session, rangeRequest);
        } catch (VersionRangeResolutionException ex) {
            throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
        }
        return rangeResult;
    }

    private static List<RemoteRepository> newRepositories(List<Channel> channels) {
        return channels.stream().map(c -> newRepository(c.getName(), c.getUrl())).collect(Collectors.toList());
    }

    private static RemoteRepository newRepository(String channel, String url) {
        RemoteRepository.Builder builder = new RemoteRepository.Builder(channel, "default", url);
        builder.setSnapshotPolicy(new org.eclipse.aether.repository.RepositoryPolicy(false, UPDATE_POLICY_NEVER, CHECKSUM_POLICY_WARN));
        builder.setReleasePolicy(new org.eclipse.aether.repository.RepositoryPolicy(true, UPDATE_POLICY_NEVER, CHECKSUM_POLICY_WARN));
        return builder.build();
    }
}
