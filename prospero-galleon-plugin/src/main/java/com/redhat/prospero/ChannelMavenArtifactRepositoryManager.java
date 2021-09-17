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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;

import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.maven.plugin.util.AbstractMavenArtifactRepositoryManager;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.util.IoUtils;

public class ChannelMavenArtifactRepositoryManager extends AbstractMavenArtifactRepositoryManager {

    private final RepositorySystemSession session;
    private final List<RemoteRepository> repositories;
    private final ProsperoArtifactResolver resolver;
    private boolean disableLatest;
    private Path tmpLocalCache;

    public ChannelMavenArtifactRepositoryManager(final RepositorySystem repoSystem, final RepositorySystemSession fallbackRepoSession,
            final List<RemoteRepository> fallBackRepositories,
            Path channels,
            boolean disableLatest, Path localCache) throws ProvisioningException {
        super(repoSystem);
        this.disableLatest = disableLatest;
        try {
            Path cache = localCache;
            if (cache == null) {
                tmpLocalCache = Files.createTempDirectory("wf-channels-repos-cache");
                cache = tmpLocalCache;
            }
            RepositorySystemSession session = newRepositorySystemSession(repoSystem, cache);
            resolver = new ProsperoArtifactResolver(channels, repoSystem, session, fallbackRepoSession, fallBackRepositories);
            this.session = fallbackRepoSession;
        } catch (IOException ex) {
            throw new ProvisioningException(ex.getLocalizedMessage(), ex);
        }

        this.repositories = resolver.getRepositories();
    }

    @Override
    protected VersionRangeResult getVersionRange(Artifact artifact) throws MavenUniverseException {
        return resolver.getVersionRange(artifact);
    }

    @Override
    public void resolve(MavenArtifact artifact) throws MavenUniverseException {
        if (disableLatest) {
            resolver.resolve(artifact);
        } else {
            resolver.resolveLatest(artifact);
        }
    }

    @Override
    public void resolveLatestVersion(MavenArtifact artifact) throws MavenUniverseException {
        resolver.resolveLatest(artifact);
    }

    @Override
    protected RepositorySystemSession getSession() throws MavenUniverseException {
        return session;
    }

    @Override
    protected List<RemoteRepository> getRepositories() throws MavenUniverseException {
        throw new MavenUniverseException("getRepositories Shouldn't be called");
    }

    public void done(Path home) throws MavenUniverseException {
        resolver.provisioningDone(home);
    }

    public void close() throws MavenUniverseException {
        if (tmpLocalCache != null) {
            System.out.println("Deleting local cache " + tmpLocalCache);
            IoUtils.recursiveDelete(tmpLocalCache);
        }
    }

    private static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, Path localCache) throws IOException {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        org.eclipse.aether.repository.LocalRepository localRepo = new LocalRepository(localCache.toFile());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }
}
