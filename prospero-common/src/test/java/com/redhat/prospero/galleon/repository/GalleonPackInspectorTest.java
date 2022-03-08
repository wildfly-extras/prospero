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

package com.redhat.prospero.galleon.repository;

import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.wfchannel.MavenSessionManager;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.jboss.galleon.ProvisioningException;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class GalleonPackInspectorTest {

    private Path basePath;
    private Path undertowModulePath;
    private InstallationMetadata installationMetadata;
    private ArtifactResult wildflyCoreFP;

    @Before
    public void setUp() throws Exception {
        // mock up local installation with undertow
        basePath = Files.createTempDirectory("eap-test");
        basePath.toFile().deleteOnExit();

        undertowModulePath = basePath.resolve("modules")
                .resolve(Paths.get("system", "layers", "base", "io", "undertow", "core", "main"));
        Files.createDirectories(undertowModulePath);
        Files.createFile(undertowModulePath.resolve("undertow-core-1.2.3.Final.jar"));

        installationMetadata = new InstallationMetadata(basePath, Arrays.asList(new DefaultArtifact("io.undertow:undertow-core:jar:1.2.3.Final")), null);

        wildflyCoreFP = downloadFeaturePack("org.wildfly.core:wildfly-core-galleon-pack:zip:17.0.0.Final");
    }

    @Test
    public void testArtifactsInGalleonPackFound() throws Exception {
        final GalleonPackInspector parser = new GalleonPackInspector(installationMetadata, basePath.resolve("modules"));

        final List<Artifact> allInstalledArtifacts = parser.getAllInstalledArtifacts(Arrays.asList(wildflyCoreFP.getArtifact().getFile().toPath()));

        assertEquals(1, allInstalledArtifacts.size());
        assertEquals("undertow-core", allInstalledArtifacts.get(0).getArtifactId());
        assertEquals(undertowModulePath.toString(), allInstalledArtifacts.get(0).getFile().getParent());
    }

    private ArtifactResult downloadFeaturePack(String coords) throws ProvisioningException, ArtifactResolutionException {
        final MavenSessionManager mavenSessionManager = new MavenSessionManager();
        final RepositorySystem repositorySystem = mavenSessionManager.newRepositorySystem();
        final DefaultRepositorySystemSession session = mavenSessionManager.newRepositorySystemSession(repositorySystem, true);

        final ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(new DefaultArtifact(coords));
        request.setRepositories(Arrays.asList(
                new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2").build()
        ));
        final ArtifactResult resolvedFp = repositorySystem.resolveArtifact(session, request);
        assertNotNull(resolvedFp.getArtifact().getFile());
        return resolvedFp;
    }
}