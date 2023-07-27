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

package org.wildfly.prospero.utils;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.deployment.DeploymentException;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class MavenUtils {

    private final RepositorySystem repositorySystem;
    private final DefaultRepositorySystemSession systemSession;

    public MavenUtils(MavenOptions mavenOptions) throws ProvisioningException {
        final MavenSessionManager msm = new MavenSessionManager(mavenOptions);
        repositorySystem = msm.newRepositorySystem();
        systemSession = msm.newRepositorySystemSession(repositorySystem);
    }
    public void deploy(ChannelManifest manifest, String groupId, String artifactId, String version, URL repository) throws DeploymentException, IOException {
        Path tempFile = null;
        try {
            final String manifestYaml = ChannelManifestMapper.toYaml(manifest);
            tempFile = Files.createTempFile("manifest", "yaml");
            Files.writeString(tempFile, manifestYaml);

            final DeployRequest deployRequest = new DeployRequest();
            deployRequest.setRepository(new RemoteRepository.Builder("test", "default", repository.toExternalForm()).build());
            deployRequest.addArtifact(new DefaultArtifact(groupId, artifactId, ChannelManifest.CLASSIFIER, ChannelManifest.EXTENSION,
                    version, null, tempFile.toFile()));
            repositorySystem.deploy(systemSession, deployRequest);
        } finally {
            if (tempFile != null) {
                FileUtils.deleteQuietly(tempFile.toFile());
            }
        }
    }

    public void deployEmptyArtifact(String groupId, String artifactId, String version, String classifier, String extension, URL repository)
            throws IOException, DeploymentException {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(artifactId + "-" + version, extension);

            final DeployRequest deployRequest = new DeployRequest();
            deployRequest.setRepository(new RemoteRepository.Builder("test", "default", repository.toExternalForm()).build());
            deployRequest.addArtifact(new DefaultArtifact(groupId, artifactId, classifier, extension,
                    version, null, tempFile.toFile()));
            repositorySystem.deploy(systemSession, deployRequest);
        } finally {
            if (tempFile != null) {
                FileUtils.deleteQuietly(tempFile.toFile());
            }
        }
    }
}
