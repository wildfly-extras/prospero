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

package org.wildfly.prospero.it.commonapi;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.junit.Test;
import org.wildfly.prospero.actions.ApplyPatchAction;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.patch.PatchArchive;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.wildfly.prospero.actions.ApplyPatchAction.PATCHES_REPO_PATH;
import static org.wildfly.prospero.actions.ApplyPatchAction.PATCH_REPO_NAME;

public class ApplyPatchActionTest extends WfCoreTestBase {

    public static final String PATCHED_ARTIFACT_ID = "wildfly-controller";
    public static final String PATCHED_ARTIFACT_GROUP = "org.wildfly.core";
    public static final String PATCHED_ARTIFACT_VERSION = "123.1.1";
    public static final String PATCHED_ARTIFACT_FILENAME = String.format("%s-%s.jar", PATCHED_ARTIFACT_ID, PATCHED_ARTIFACT_VERSION);
    private Path provisionConfigFile;

    @Test
    public void testInstallSimplePatch() throws Exception {
        // use artifact not in LOCAL_MAVEN_REPO to make sure patch repository is being used
        final Artifact mockArtifact = mockUpgradeArtifact();

        // build patch zip
        final File patchArchive = temp.newFile("patch-test.zip");
        PatchArchive.createPatchArchive(
                Collections.singletonList(mockArtifact),
                patchArchive,
                "patch-test00001");

        // installCore
        provisionConfigFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(provisionConfigFile)
                .build();
        installation.provision(provisioningDefinition);

        // apply patch
        final ApplyPatchAction applyPatchAction = new ApplyPatchAction(outputPath, mavenSessionManager, new AcceptingConsole());
        applyPatchAction.apply(patchArchive.toPath());

        // verify config changed - patch channel & local repository
        final Path metadataDir = outputPath.resolve(InstallationMetadata.METADATA_DIR);
        final ProsperoConfig prosperoConfig = ProsperoConfig.readConfig(metadataDir.resolve(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME));
        assertThat(prosperoConfig.getChannels()).containsExactly(
                new ChannelRef(null, outputPath.resolve(".patches").resolve("patch-test00001-channel.yaml").toUri().toURL().toString()),
                new ChannelRef(null, ApplyPatchActionTest.class.getClassLoader().getResource(CHANNEL_BASE_CORE_19).toString())
        );

        assertThat(prosperoConfig.getRepositories()).contains(
                new RepositoryRef(PATCH_REPO_NAME, outputPath.resolve(PATCHES_REPO_PATH).toUri().toURL().toString())
        );
        // verify artifact changed in manifest
        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest(metadataDir.resolve(InstallationMetadata.MANIFEST_FILE_NAME),
                PATCHED_ARTIFACT_GROUP, PATCHED_ARTIFACT_ID);
        assertEquals(PATCHED_ARTIFACT_VERSION, wildflyCliArtifact.get().getVersion());
    }

    private Artifact mockUpgradeArtifact() throws IOException, ArtifactResolutionException {
        final File target = temp.newFolder();
        final Path mockArtifactFile = target.toPath().resolve(PATCHED_ARTIFACT_FILENAME);
        final Artifact artifact = resolveArtifact(PATCHED_ARTIFACT_GROUP, PATCHED_ARTIFACT_ID, WfCoreTestBase.BASE_VERSION);
        Files.copy(artifact.getFile().toPath(), mockArtifactFile);
        final Artifact mockArtifact = artifact.setFile(mockArtifactFile.toFile()).setVersion(PATCHED_ARTIFACT_VERSION);
        return mockArtifact;
    }

    private Optional<Artifact> readArtifactFromManifest(Path manifestFile, String groupId, String artifactId) throws IOException {
        return ManifestYamlSupport.parse(manifestFile.toFile()).getStreams().stream()
                .filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst()
                .map(s->new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion()));
    }
}