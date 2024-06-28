/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.it.cli;

import org.apache.commons.io.FileUtils;
import org.jboss.galleon.util.ZipUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.ExecutionUtils;
import org.wildfly.prospero.it.commonapi.WfCoreTestBase;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.wildfly.prospero.test.MetadataTestUtils.upgradeStreamInManifest;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class ApplyUpdateTest extends CliTestBase  {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private File targetDir;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        targetDir = tempDir.newFolder();
    }

    @Test
    public void generateUpdateAndApply() throws Exception {
        final Path manifestPath = temp.newFile().toPath();
        final Path provisionConfig = temp.newFile().toPath();
        final Path updatePath = tempDir.newFolder("update-candidate").toPath();
        MetadataTestUtils.copyManifest("manifests/wfcore-base.yaml", manifestPath);
        MetadataTestUtils.prepareChannel(provisionConfig, List.of(manifestPath.toUri().toURL()), defaultRemoteRepositories());

        install(provisionConfig, targetDir.toPath());

        upgradeStreamInManifest(manifestPath, resolvedUpgradeArtifact);

        final URL temporaryRepo = mockTemporaryRepo(true);

        // generate update-candidate
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.PREPARE,
                        CliConstants.REPOSITORIES, temporaryRepo.toString(),
                        CliConstants.CANDIDATE_DIR, updatePath.toAbsolutePath().toString(),
                        CliConstants.Y,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has not been modified
        Optional<Stream> wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.BASE_VERSION, wildflyCliStream.get().getVersion());

        // apply update-candidate
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                        CliConstants.CANDIDATE_DIR, updatePath.toAbsolutePath().toString(),
                        CliConstants.Y,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has been modified
        wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.UPGRADE_VERSION, wildflyCliStream.get().getVersion());
    }

    @Test
    public void generateUpdateAndApplyUsingRepositoryArchive() throws Exception {
        final Path manifestPath = temp.newFile().toPath();
        final Path provisionConfig = temp.newFile().toPath();
        final Path updatePath = tempDir.newFolder("update-candidate").toPath();
        MetadataTestUtils.copyManifest("manifests/wfcore-base.yaml", manifestPath);
        MetadataTestUtils.prepareChannel(provisionConfig, List.of(manifestPath.toUri().toURL()), defaultRemoteRepositories());

        install(provisionConfig, targetDir.toPath());

        upgradeStreamInManifest(manifestPath, resolvedUpgradeArtifact);

        final URL temporaryRepo = mockTemporaryRepo(true);
        final Path repoArchive = createRepositoryArchive(temporaryRepo);

        // generate update-candidate
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.PREPARE,
                        CliConstants.REPOSITORIES, repoArchive.toUri().toString(),
                        CliConstants.CANDIDATE_DIR, updatePath.toAbsolutePath().toString(),
                        CliConstants.Y,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has not been modified
        Optional<Stream> wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.BASE_VERSION, wildflyCliStream.get().getVersion());

        // apply update-candidate
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                        CliConstants.CANDIDATE_DIR, updatePath.toAbsolutePath().toString(),
                        CliConstants.Y,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has been modified
        wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.UPGRADE_VERSION, wildflyCliStream.get().getVersion());
    }

    @Test
    public void generateUpdateAndApplyIntoSymbolicLink() throws Exception {
        final Path manifestPath = temp.newFile().toPath();
        final Path provisionConfig = temp.newFile().toPath();
        final Path updatePath = tempDir.newFolder("update-candidate").toPath();
        MetadataTestUtils.copyManifest("manifests/wfcore-base.yaml", manifestPath);
        MetadataTestUtils.prepareChannel(provisionConfig, List.of(manifestPath.toUri().toURL()), defaultRemoteRepositories());

        final Path targetLink = Files.createSymbolicLink(temp.newFolder().toPath().resolve("target-link"), targetDir.toPath());
        final Path candidateLink = Files.createSymbolicLink(temp.newFolder().toPath().resolve("update-candidate-link"), updatePath);

        install(provisionConfig, targetLink);

        upgradeStreamInManifest(manifestPath, resolvedUpgradeArtifact);

        final URL temporaryRepo = mockTemporaryRepo(true);

        // generate update-candidate
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.PREPARE,
                        CliConstants.REPOSITORIES, temporaryRepo.toString(),
                        CliConstants.CANDIDATE_DIR, candidateLink.toAbsolutePath().toString(),
                        CliConstants.Y,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has not been modified
        Optional<Stream> wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.BASE_VERSION, wildflyCliStream.get().getVersion());

        // apply update-candidate
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                        CliConstants.CANDIDATE_DIR, candidateLink.toAbsolutePath().toString(),
                        CliConstants.Y,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has been modified
        wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.UPGRADE_VERSION, wildflyCliStream.get().getVersion());
    }

    private Path createRepositoryArchive(URL temporaryRepo) throws URISyntaxException, IOException {
        final Path repoPath = Path.of(temporaryRepo.toURI());
        final Path root = tempDir.newFolder("repo-root").toPath();
        FileUtils.createParentDirectories(root.resolve("update-repository").resolve("maven-repository").toFile());
        Files.move(repoPath, root.resolve("update-repository").resolve("maven-repository"));
        ZipUtils.zip(root, root.resolve("repository.zip"));
        return root.resolve("repository.zip");
    }
}
