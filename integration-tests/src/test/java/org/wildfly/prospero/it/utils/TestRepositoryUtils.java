/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.it.utils;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

public class TestRepositoryUtils {

    private final String repositoryUrl;
    private final RepositorySystem repoSystem;
    private final DefaultRepositorySystemSession repoSession;

    public TestRepositoryUtils(URL repository) throws ProvisioningException {
        this.repositoryUrl = repository.toExternalForm();
        final MavenSessionManager msm = new MavenSessionManager(MavenOptions.OFFLINE_NO_CACHE);
        repoSystem = msm.newRepositorySystem();
        repoSession = msm.newRepositorySystemSession(repoSystem);
    }

    public TestRepositoryUtils(Path repository) throws ProvisioningException, MalformedURLException {
        this(repository.toUri().toURL());
    }

    public void deployArtifact(Artifact artifact) throws MalformedURLException, DeploymentException {
        final DeployRequest request = new DeployRequest();
        request.addArtifact(artifact);
        request.setRepository(new RemoteRepository.Builder("test-repo", "default", repositoryUrl).build());
        repoSystem.deploy(repoSession, request);
    }

    public void removeArtifact(String groupId, String artifactId) throws IOException {
        try {
            FileUtils.deleteDirectory(Path.of(new URI(repositoryUrl)).resolve(groupId.replace('.', File.separatorChar)).resolve(artifactId).toFile());
        } catch (URISyntaxException e) {
            // already validated
            throw new RuntimeException(e);
        }
    }
}
