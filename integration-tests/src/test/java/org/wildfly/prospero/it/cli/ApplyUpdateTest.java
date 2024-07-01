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
import org.assertj.core.internal.BinaryDiff;
import org.assertj.core.internal.Diff;
import org.assertj.core.util.diff.Delta;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.wildfly.prospero.it.ExecutionUtils.isWindows;
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
                        CliConstants.Y, CliConstants.VERBOSE,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);

        // verify the original server has been modified
        wildflyCliStream = getInstalledArtifact(resolvedUpgradeArtifact.getArtifactId(), targetDir.toPath());
        assertEquals(WfCoreTestBase.UPGRADE_VERSION, wildflyCliStream.get().getVersion());
    }

    @Test
    public void failedApplyCandidate_ShouldRevertAllFileChanges() throws Exception {
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


        final File originalServer = temp.newFolder("server-copy");
        FileUtils.copyDirectory(targetDir,originalServer);
        final Path protectedPath = targetDir.toPath().resolve(".installation");
        try {
            // lock a resource that would be modified to force the apply to fail
            lockPath(protectedPath, false);

            // apply update-candidate
            ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.APPLY,
                            CliConstants.CANDIDATE_DIR, updatePath.toAbsolutePath().toString(),
                            CliConstants.Y, CliConstants.VERBOSE,
                            CliConstants.DIR, targetDir.getAbsolutePath())
                    .execute()
                    .assertReturnCode(ReturnCodes.PROCESSING_ERROR)
                    .assertErrorContains("java.nio.file.AccessDeniedException");
        } finally {
            lockPath(protectedPath, true);
        }

        assertNoChanges(originalServer.toPath(), targetDir.toPath());
    }

    private static void lockPath(Path protectedPath, boolean writable) {
        if (isWindows()) {
            // On Windows we can't set a directory to be read-only so we have to set a single file.
            // in this case manifest.yaml will have to be updated
            assertTrue("Unable to set the read-only file permissions", protectedPath.resolve("manifest.yaml").toFile().setWritable(writable));
        } else {
            // On Linux for a change setting a file to be read-only doesn't prevent it from being overwritten
            assertTrue("Unable to set the read-only file permissions", protectedPath.toFile().setWritable(writable));
        }
    }

    private static class FileChange {
        Path expected;
        Path actual;

        public FileChange(Path expected, Path actual) {
            this.expected = expected;
            this.actual = actual;
        }
    }

    private void assertNoChanges(Path originalServer, Path targetDir) throws IOException {
        final List<FileChange> changes = new ArrayList<>();
        final BinaryDiff binaryDiff = new BinaryDiff();

        // get a list of files present only in the expected server or ones present in both but with different content
        Files.walkFileTree(originalServer, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                final Path relative = originalServer.relativize(file);
                final Path actualFile = targetDir.resolve(relative);
                if (!Files.exists(actualFile)) {
                    changes.add(new FileChange(file, null));
                } else if (binaryDiff.diff(actualFile, Files.readAllBytes(file)).hasDiff()) {
                    changes.add(new FileChange(file, actualFile));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                final Path relative = originalServer.relativize(dir);
                final Path actualFile = targetDir.resolve(relative);
                if (!Files.exists(actualFile)) {
                    changes.add(new FileChange(dir, null));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // get a list of files present only in the actual server
        Files.walkFileTree(targetDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                final Path relative = targetDir.relativize(file);
                final Path expectedFile = originalServer.resolve(relative);
                if (!Files.exists(expectedFile)) {
                    changes.add(new FileChange(null, file));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                final Path relative = targetDir.relativize(dir);
                final Path expectedFile = originalServer.resolve(relative);
                if (!Files.exists(expectedFile)) {
                    changes.add(new FileChange(null, dir));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (!changes.isEmpty()) {
            final StringBuilder sb = new StringBuilder("Expected folders to be the same, but:\n");
            final Diff textDiff = new Diff();

            for (FileChange change : changes) {
                if (change.actual == null) {
                    sb.append(" [R] ").append(originalServer.relativize(change.expected)).append("\n");
                } else if (change.expected == null) {
                    sb.append(" [A] ").append(targetDir.relativize(change.actual)).append("\n");
                } else {
                    sb.append(" [M] ").append(targetDir.relativize(change.actual)).append("\n");
                    final String fileName = change.actual.toString();
                    if (fileName.endsWith("xml") || fileName.endsWith("txt") || fileName.endsWith("yaml")) {
                        final List<Delta<String>> diff = textDiff.diff(change.actual, StandardCharsets.UTF_8, change.expected, StandardCharsets.UTF_8);
                        diff.forEach(d->{
                            sb.append("    Line ").append(d.lineNumber()).append(":").append("\n");
                            sb.append("      - ").append(String.join("\n      - ", d.getOriginal().getLines())).append("\n");
                            sb.append("      + ").append(String.join("\n      + ", d.getRevised().getLines())).append("\n");
                        });
                    }
                }
            }
            fail(sb.toString());
        }
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
