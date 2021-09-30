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
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

import com.redhat.prospero.api.Artifact;
import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Repository;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.version.Version;
import org.jboss.galleon.universe.maven.MavenUniverseException;

public class MavenRepository implements Repository {

    private final RepositorySystem repoSystem;
    private final RepositorySystemSession repoSession;
    private final List<RemoteRepository> repositories;

    public MavenRepository(List<Channel> channels) {
        this.repositories = newRepositories(channels);
        try {
            repoSystem = newRepositorySystem();
            repoSession = newRepositorySystemSession(repoSystem);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MavenRepository(RepositorySystem repositorySystem, List<Channel> channels) {
        this.repoSystem = repositorySystem;
        this.repositories = newRepositories(channels);
        try {
            this.repoSession = newRepositorySystemSession(repoSystem);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MavenRepository(List<RemoteRepository> repositories, RepositorySystem repositorySystem) {
        this.repositories = repositories;
        this.repoSystem = repositorySystem;
        try {
            this.repoSession = newRepositorySystemSession(repoSystem);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public File resolve(Artifact artifact) throws ArtifactNotFoundException {
        ArtifactRequest req = new ArtifactRequest();
        req.setArtifact(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getPackaging(), artifact.getVersion()));
        req.setRepositories(newRepositories());
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

    @Override
    public Artifact findLatestVersionOf(Artifact artifact) {
        VersionRangeRequest req = new VersionRangeRequest();
        // TODO: if already a range do not change it
        final DefaultArtifact artifact1 = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getPackaging(), "[" + artifact.getVersion() + ",)");
        req.setArtifact(artifact1);
        req.setRepositories(newRepositories());

        try {
            final VersionRangeResult versionRangeResult = repoSystem.resolveVersionRange(repoSession, req);
            final Version highestVersion = versionRangeResult.getHighestVersion();
            if (highestVersion == null) {
                return null;
            } else {
                return artifact.newVersion(highestVersion.toString());
            }
        } catch (VersionRangeResolutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public VersionRangeResult getVersionRange(Artifact artifact) throws MavenUniverseException {
        final DefaultArtifact artifact1 = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getPackaging(), artifact.getVersion());
        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact(artifact1);
        rangeRequest.setRepositories(newRepositories());
        VersionRangeResult rangeResult;
        try {
            rangeResult = repoSystem.resolveVersionRange(repoSession, rangeRequest);
        } catch (VersionRangeResolutionException ex) {
            throw new MavenUniverseException(ex.getLocalizedMessage(), ex);
        }
        return rangeResult;
    }

    private static RepositorySystem newRepositorySystem() {
        /*
         * Aether's components implement org.eclipse.aether.spi.locator.Service to ease manual wiring and using the
         * prepopulated DefaultServiceLocator, we only need to register the repository connector and transporter
         * factories.
         */
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                System.out.println(String.format("Service creation failed for %s with implementation %s",
                        type, impl));
                exception.printStackTrace();
            }
        });

        return locator.getService(RepositorySystem.class);
    }

    private static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) throws IOException {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        org.eclipse.aether.repository.LocalRepository localRepo = new LocalRepository(Files.createTempDirectory("mvn-repo").toString());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        return session;
    }

    public List<RemoteRepository> newRepositories() {
        return repositories;
    }

    public List<RemoteRepository> newRepositories(List<Channel> channels) {
        return channels.stream().map(c -> newRepository(c.getName(), c.getUrl())).collect(Collectors.toList());
    }

    private RemoteRepository newRepository(String channel, String url) {
        return new RemoteRepository.Builder(channel, "default", url).build();
    }
}
