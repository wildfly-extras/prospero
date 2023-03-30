/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.cli;

import org.apache.commons.codec.digest.DigestUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class ArtifactResolutionAnalyzerTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private RepositorySystem system;
    private DefaultRepositorySystemSession session;
    private File repo;
    private RemoteRepository repositoryDefinition;

    @Before
    public void setUp() throws Exception {
        final MavenSessionManager mavenSessionManager = new MavenSessionManager(MavenOptions.OFFLINE_NO_CACHE);

        system = mavenSessionManager.newRepositorySystem();
        session = mavenSessionManager.newRepositorySystemSession(system);

        repo = temp.newFolder();
        final RepositoryPolicy failPolicy = new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_FAIL);
        repositoryDefinition = new RemoteRepository.Builder("test", "default", repo.toURI().toURL().toExternalForm())
                .setPolicy(failPolicy)
                .build();
    }

    @Test
    public void testMissingArtifact() throws Exception {
        final ArtifactRequest req = getArtifactRequest("idont", "exist");

        ArtifactResolutionException are = getResolveException(List.of(req), "idont", "exist");
        final List<ArtifactResolutionAnalyzer.Result> results = new ArtifactResolutionAnalyzer().analyze(are);

        assertThat(results).containsOnly(
                new ArtifactResolutionAnalyzer.Result("idont:exist:jar:1.0.0.Final", "missing")
        );
    }

    @Test
    public void testChecksumArtifact() throws Exception {
        mockDeployWithoutChecksum("invalid", "checksum");

        final ArtifactRequest req = getArtifactRequest("invalid", "checksum");

        ArtifactResolutionException are = getResolveException(List.of(req), "invalid", "checksum");

        final List<ArtifactResolutionAnalyzer.Result> results = new ArtifactResolutionAnalyzer().analyze(are);

        assertThat(results).containsOnly(
                new ArtifactResolutionAnalyzer.Result("invalid:checksum:jar:1.0.0.Final", "checksum failed")
        );
    }

    @Test
    public void testMixedArtifacts() throws Exception {
        mockDeployWithoutChecksum("invalid", "checksum");
        mockDeploy("valid", "artifact");

        final ArtifactRequest req1 = getArtifactRequest("invalid", "checksum");
        final ArtifactRequest req2 = getArtifactRequest("valid", "artifact");
        final ArtifactRequest req3 = getArtifactRequest("idont", "exist");

        ArtifactResolutionException are = getResolveException(List.of(req1, req2, req3), "invalid", "checksum");

        final List<ArtifactResolutionAnalyzer.Result> results = new ArtifactResolutionAnalyzer().analyze(are);

        assertThat(results).containsExactlyInAnyOrder(
                new ArtifactResolutionAnalyzer.Result("invalid:checksum:jar:1.0.0.Final", "checksum failed"),
                new ArtifactResolutionAnalyzer.Result("idont:exist:jar:1.0.0.Final", "missing")
        );
    }

    private ArtifactResolutionException getResolveException(List<ArtifactRequest> requests, String groupId, String artifactId) {
        try {
            system.resolveArtifacts(session, requests);
            throw new RuntimeException("The artifact doesn't exist - should throw an exception");
        } catch (org.eclipse.aether.resolution.ArtifactResolutionException ex) {
            final Set<ArtifactCoordinate> missingArtifacts = ex.getResults().stream()
                    .filter(r->!r.isResolved())
                    .map(r->r.getRequest().getArtifact())
                    .map(a->new ArtifactCoordinate(a.getGroupId(), a.getArtifactId(), a.getExtension(), a.getClassifier(), a.getVersion()))
                    .collect(Collectors.toSet());
            return new ArtifactResolutionException("", ex,
                    missingArtifacts,
                    Collections.<Repository>emptySet(), false);
        }
    }

    private ArtifactRequest getArtifactRequest(String groupId, String artifactId) {
        final Artifact artifact = new DefaultArtifact(groupId, artifactId, "jar", "1.0.0.Final");
        return new ArtifactRequest(artifact, List.of(repositoryDefinition), null);
    }

    private void mockDeployWithoutChecksum(String groupId, String artifactId) throws IOException {
        final Path artifactPath = Path.of(groupId, artifactId, "1.0.0.Final");
        Files.createDirectories(repo.toPath().resolve(artifactPath));
        Files.createFile(repo.toPath().resolve(artifactPath).resolve(artifactId + "-1.0.0.Final.jar"));
    }

    private void mockDeploy(String groupId, String artifactId) throws Exception {
        final Path artifactPath = Path.of(groupId, artifactId, "1.0.0.Final");
        Files.createDirectories(repo.toPath().resolve(artifactPath));
        final Path jarFile = repo.toPath().resolve(artifactPath).resolve(artifactId + "-1.0.0.Final.jar");
        Files.createFile(jarFile);
        Files.writeString(repo.toPath().resolve(artifactPath).resolve(artifactId + "-1.0.0.Final.jar.sha1"), createSha1(jarFile.toFile()));
    }

    public String createSha1(File file) throws Exception {
        InputStream fis = new FileInputStream(file);
        return DigestUtils.sha1Hex(fis);
    }
}