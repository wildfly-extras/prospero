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

import com.redhat.prospero.api.Resolver;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class DefaultResolver implements Resolver {

    private final RepositorySystem repoSystem;
    private final RepositorySystemSession repoSession;
    private final List<RemoteRepository> repositories;

    public DefaultResolver(List<RemoteRepository> repositories, RepositorySystem repoSystem, RepositorySystemSession repoSession) {
        this.repositories = repositories;
        this.repoSystem = repoSystem;
        this.repoSession = repoSession;
    }

    @Override
    public ArtifactResult resolve(Artifact artifact) throws ArtifactResolutionException {
        ArtifactRequest req = new ArtifactRequest();
        req.setArtifact(artifact);
        req.setRepositories(repositories);
        return repoSystem.resolveArtifact(repoSession, req);
    }

    @Override
    public VersionRangeResult getVersionRange(Artifact artifact) throws VersionRangeResolutionException {
        VersionRangeRequest req = new VersionRangeRequest();
        req.setArtifact(artifact);
        req.setRepositories(repositories);

        final VersionRangeResult versionRangeResult = repoSystem.resolveVersionRange(repoSession, req);
        return versionRangeResult;
    }

    private static DefaultRepositorySystemSession defaultRepositorySystemSession(RepositorySystem system) {
        try {
            DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

            org.eclipse.aether.repository.LocalRepository localRepo = new LocalRepository(Files.createTempDirectory("mvn-repo").toString());
            session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

            return session;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
