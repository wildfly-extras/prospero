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

package com.redhat.prospero.wfchannel;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
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
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.channel.MavenRepository;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.channel.spi.MavenVersionsResolver;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

public class WfChannelMavenResolver implements MavenVersionsResolver {
    static String LOCAL_MAVEN_REPO = System.getProperty("user.home") + "/.m2/repository";

    public static final Logger logger = LoggerFactory.getLogger(WfChannelMavenResolver.class);

    private final RepositorySystem system;
    private final DefaultRepositorySystemSession session;

    private final List<RemoteRepository> remoteRepositories;

    WfChannelMavenResolver(List<MavenRepository> mavenRepositories, boolean resolveLocalCache) {
        remoteRepositories = mavenRepositories.stream().map(r -> newRemoteRepository(r)).collect(Collectors.toList());
        system = newRepositorySystem();
        session = newRepositorySystemSession(system, resolveLocalCache);
    }

    @Override
    public Set<String> getAllVersions(String groupId, String artifactId, String extension, String classifier) {
        requireNonNull(groupId);
        requireNonNull(artifactId);
        logger.trace("Resolving the latest version of %s:%s in repositories: %s",
                     groupId, artifactId, remoteRepositories.stream().map(r -> r.getUrl()).collect(Collectors.joining(",")));

        Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, "[0,)");
        VersionRangeRequest versionRangeRequest = new VersionRangeRequest();
        versionRangeRequest.setArtifact(artifact);
        versionRangeRequest.setRepositories(remoteRepositories);

        try {
            VersionRangeResult versionRangeResult = system.resolveVersionRange(session, versionRangeRequest);
            Set<String> versions = versionRangeResult.getVersions().stream().map(Version::toString).collect(Collectors.toSet());
            logger.trace("All versions in the repositories: %s", versions);
            return versions;
        } catch (VersionRangeResolutionException e) {
            return emptySet();
        }
    }

    @Override
    public File resolveArtifact(String groupId, String artifactId, String extension, String classifier, String version) throws UnresolvedMavenArtifactException {
        Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, extension, version);

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(artifact);
        request.setRepositories(remoteRepositories);
        try {
            ArtifactResult result = system.resolveArtifact(session, request);
            return result.getArtifact().getFile();
        } catch (ArtifactResolutionException e) {
            UnresolvedMavenArtifactException umae = new UnresolvedMavenArtifactException();
            umae.initCause(e);
            throw umae;
        }
    }

    public static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                exception.printStackTrace();
            }
        });
        return locator.getService(RepositorySystem.class);
    }

    public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, boolean resolveLocalCache) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        String location;
        if (resolveLocalCache) {
            location = LOCAL_MAVEN_REPO;
        } else {
            location = "target/local-repo" ;
        }
        LocalRepository localRepo = new LocalRepository(location);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }

    private static RemoteRepository newRemoteRepository(MavenRepository mavenRepository) {
        return new RemoteRepository.Builder(mavenRepository.getId(), "default", mavenRepository.getUrl().toExternalForm()).build();
    }}
