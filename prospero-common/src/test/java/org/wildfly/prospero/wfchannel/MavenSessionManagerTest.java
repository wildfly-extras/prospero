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

package org.wildfly.prospero.wfchannel;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.api.MavenOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MavenSessionManagerTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void defaultToMavenHome() throws Exception {
        final MavenSessionManager msm = new MavenSessionManager(MavenOptions.DEFAULT_OPTIONS);
        assertEquals(MavenSessionManager.LOCAL_MAVEN_REPO, msm.getProvisioningRepo());
    }

    @Test
    public void useTempFolderIfNoCacheOptionSet() throws Exception {
        final MavenSessionManager msm = new MavenSessionManager(MavenOptions.builder().setNoLocalCache(true).build());

        // JDK 17 reads the java.io.tmpdir property before it's altered by mvn
        final Path test = Files.createTempDirectory("test");
        final Path defaultTempPath = test.getParent();
        Files.delete(test);

        assertTrue(msm.getProvisioningRepo().toString() + " should start with  " + defaultTempPath, msm.getProvisioningRepo().startsWith(defaultTempPath));
    }

    @Test
    public void ignoreLocalArtifactsWhenResolvingVersions() throws Exception {
        final Path cachePath = temp.newFolder().toPath();
        final Path repoPath = temp.newFolder().toPath();
        final MavenSessionManager msm = new MavenSessionManager(MavenOptions.builder()
                .setLocalCachePath(cachePath)
                .build());

        final RepositorySystem system = msm.newRepositorySystem();
        final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system);

        Artifact artifact = new DefaultArtifact("test", "test", "jar", "1.2.3");
        artifact = artifact.setFile(temp.newFile());

        final InstallRequest installRequest = new InstallRequest();
        installRequest.addArtifact(artifact);
        system.install(session, installRequest);

        final VersionRangeRequest request = new VersionRangeRequest();
        request.setArtifact(new DefaultArtifact("test", "test", "jar", "[1.0.0,)"));
        request.setRepositories(List.of(new RemoteRepository.Builder("test", "default", repoPath.toUri().toURL().toExternalForm()).build()));
        VersionRangeResult artifactResult = system.resolveVersionRange(session, request);

        assertTrue(artifactResult.getVersions().isEmpty());

        final DeployRequest deployRequest = new DeployRequest();
        deployRequest.addArtifact(artifact);
        deployRequest.setRepository(new RemoteRepository.Builder("test", "default", repoPath.toUri().toURL().toExternalForm()).build());
        system.deploy(session, deployRequest);

        artifactResult = system.resolveVersionRange(session, request);

        assertThat(artifactResult.getVersions())
                .map(Version::toString)
                .contains("1.2.3");
    }
}