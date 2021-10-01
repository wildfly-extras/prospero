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
package com.redhat.prospero.galleon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Repository;
import com.redhat.prospero.impl.repository.FallbackMavenRepository;
import com.redhat.prospero.impl.repository.MavenRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.maven.plugin.util.AbstractMavenArtifactRepositoryManager;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.util.IoUtils;

import static com.redhat.prospero.api.ArtifactUtils.from;

public class ChannelMavenArtifactRepositoryManager extends AbstractMavenArtifactRepositoryManager implements AutoCloseable {
    private static final Logger log = LogManager.getLogger(ChannelMavenArtifactRepositoryManager.class);

    private final RepositorySystemSession session;
    private final Repository repository;
    private boolean disableLatest;
    private Path tmpLocalCache;

    private final Set<MavenArtifact> resolvedArtifacts = new HashSet<>();

    public ChannelMavenArtifactRepositoryManager(final RepositorySystem repoSystem, final RepositorySystemSession fallbackRepoSession,
                                                 final List<RemoteRepository> fallBackRepositories,
                                                 final List<Channel> channels,
                                                 boolean disableLatest, Path localCache) throws ProvisioningException {
        super(repoSystem);
        this.disableLatest = disableLatest;
        try {
            Path cache = localCache;
            if (cache == null) {
                tmpLocalCache = Files.createTempDirectory("wf-channels-repos-cache");
                cache = tmpLocalCache;
            }

            Repository prosperoRepository = new MavenRepository(repoSystem, channels);
            Repository fallbackRepository = new MavenRepository(fallBackRepositories, repoSystem);
            repository = new FallbackMavenRepository(prosperoRepository, fallbackRepository);
            this.session = fallbackRepoSession;
        } catch (IOException ex) {
            throw new ProvisioningException(ex.getLocalizedMessage(), ex);
        }
    }

    @Override
    protected VersionRangeResult getVersionRange(Artifact artifact) throws MavenUniverseException {
        return repository.getVersionRange(artifact);
    }

    @Override
    public void resolve(MavenArtifact artifact) throws MavenUniverseException {
        if (disableLatest) {
            doResolve(artifact);
        } else {
            resolveLatestVersion(artifact);
        }
    }

    private void doResolve(MavenArtifact artifact) throws MavenUniverseException {
        if (artifact.isResolved()) {
            throw new MavenUniverseException("Artifact is already resolved");
        }
        if (artifact.getVersion() == null) {
            throw new MavenUniverseException("Version is not set for " + artifact);
        }
        if (artifact.getVersionRange() != null) {
            log.warn("WARNING: Version range is set for {}", artifact);
        }

        try {
            final File resolvedPath = repository.resolve(from(artifact));
            artifact.setPath(resolvedPath.toPath());

            log.info("RESOLVED: " + artifact);
            if ("jar".equals(artifact.getExtension())) {
                resolvedArtifacts.add(artifact);
            }
        } catch (ArtifactNotFoundException e) {
            throw new MavenUniverseException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public void resolveLatestVersion(MavenArtifact mavenArtifact) throws MavenUniverseException {
        if (mavenArtifact.isResolved()) {
            throw new MavenUniverseException("Artifact is already resolved");
        }
        // TODO: handle range below
        String range;
        if (mavenArtifact.getVersionRange() == null) {
            if (mavenArtifact.getVersion() == null) {
                throw new MavenUniverseException("Can't compute range, version is not set for " + mavenArtifact);
            }
            range = "[" + mavenArtifact.getVersion() + ",)";
        } else {
            if (mavenArtifact.getVersion() != null) {
                log.info("Version is set for {} although a range is provided {}. Using provided range." + mavenArtifact, mavenArtifact.getVersionRange());
            }
            range = mavenArtifact.getVersionRange();
        }

        try {
            final Artifact artifact = from(mavenArtifact);
            Artifact resolved = repository.resolveLatestVersionOf(artifact);
            if (resolved == null) {
                throw new MavenUniverseException("Artifact is not found " + mavenArtifact.getGroupId() + ":" + mavenArtifact.getArtifactId());
            } else {
                artifact.setVersion(resolved.getVersion());
                mavenArtifact.setVersion(resolved.getVersion());
                mavenArtifact.setPath(resolved.getFile().toPath());
            }
            log.debug("LATEST: Found version {} for range {}", mavenArtifact.getVersion(), range);
        } catch (ArtifactNotFoundException ex) {
            throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
        }
        if ("jar".equals(mavenArtifact.getExtension())) {
            resolvedArtifacts.add(mavenArtifact);
        }
    }

    @Override
    protected RepositorySystemSession getSession() throws MavenUniverseException {
        return session;
    }

    @Override
    protected List<RemoteRepository> getRepositories() throws MavenUniverseException {
        throw new MavenUniverseException("getRepositories Shouldn't be called");
    }

    @Override
    public void close() throws MavenUniverseException {
        if (tmpLocalCache != null) {
            log.debug("Deleting local cache " + tmpLocalCache);
            IoUtils.recursiveDelete(tmpLocalCache);
        }
    }

    public Set<MavenArtifact> resolvedArtfacts() {
        return resolvedArtifacts;
    }
}
