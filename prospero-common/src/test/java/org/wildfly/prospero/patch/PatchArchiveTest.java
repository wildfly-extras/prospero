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

package org.wildfly.prospero.patch;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProvisioningConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.wildfly.prospero.actions.ApplyPatchAction.PATCHES_REPO_PATH;
import static org.wildfly.prospero.api.InstallationMetadata.METADATA_DIR;
import static org.wildfly.prospero.api.InstallationMetadata.PROSPERO_CONFIG_FILE_NAME;

public class PatchArchiveTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();


    @Test
    public void extractPatchFileIntoServer() throws Exception {
        // mock up server
        final Path server = mockUpServer();

        // create archive
        final Path patchArchive = createPatchArchive();
        final PatchArchive archive = new PatchArchive(patchArchive);

        // install
        final Patch patchFile = archive.extract(server);

        // should have .patches/channels with channel file and .patches/repository with the repository content
        assertTrue(Files.exists(new Patch(server, "patch-test00001-channel.yaml").getChannelFilePath()));
        assertEquals(new Patch(server, "patch-test00001-channel.yaml").getChannelFilePath(), patchFile.getChannelFilePath());
        assertTrue(Files.exists(server.resolve(PATCHES_REPO_PATH).resolve(Paths.get("foo/bar/test/1.2.3/test-1.2.3.jar"))));
    }

    @Test
    public void extractPatchFileIntoServerWithExistingPatchRepository() throws Exception {
        // mock up server
        final Path server = mockUpServer();
        Files.createDirectories(server.resolve(PATCHES_REPO_PATH).resolve(Paths.get("existing/artifact/1.2.3")));
        Files.createFile(server.resolve(PATCHES_REPO_PATH).resolve(Paths.get("existing/artifact/1.2.3")).resolve("artifact-1.2.3.jar"));

        // create archive
        final Path patchArchive = createPatchArchive();
        final PatchArchive archive = new PatchArchive(patchArchive);

        // install
        archive.extract(server);

        assertTrue(Files.exists(server.resolve(PATCHES_REPO_PATH).resolve(Paths.get("foo/bar/test/1.2.3/test-1.2.3.jar"))));
        assertTrue(Files.exists(server.resolve(PATCHES_REPO_PATH).resolve(Paths.get("existing/artifact/1.2.3/artifact-1.2.3.jar"))));
    }

    @Test
    public void testExistingArtifactIsAcceptedIfTheSame() throws Exception {
        // mock up server
        final Path server = mockUpServer();
        Files.createDirectories(server.resolve(PATCHES_REPO_PATH).resolve(Paths.get("foo/bar/test/1.2.3")));
        Files.createFile(server.resolve(PATCHES_REPO_PATH).resolve(Paths.get("foo/bar/test/1.2.3")).resolve("test-1.2.3.jar"));

        // create archive
        final Path patchArchive = createPatchArchive();
        final PatchArchive archive = new PatchArchive(patchArchive);

        // install
        archive.extract(server);

        assertTrue(Files.exists(server.resolve(PATCHES_REPO_PATH).resolve(Paths.get("foo/bar/test/1.2.3/test-1.2.3.jar"))));
    }

    @Test
    public void testThrowExceptionIfExistingArtifactIsDifferentFromNew() throws Exception {
        // mock up server
        final Path server = mockUpServer();
        Files.createDirectories(server.resolve(PATCHES_REPO_PATH).resolve(Paths.get("foo/bar/test/1.2.3")));
        Files.writeString(server.resolve(PATCHES_REPO_PATH).resolve(Paths.get("foo/bar/test/1.2.3")).resolve("test-1.2.3.jar"),
                "foo", StandardOpenOption.CREATE_NEW);

        // create archive
        final Path patchArchive = createPatchArchive();
        final PatchArchive archive = new PatchArchive(patchArchive);

        // install
        try {
            archive.extract(server);
            Assert.fail("Cannot extract with conflicting artifacts in repository");
        } catch (IllegalArgumentException e) {
            // OK, ignore
        }
    }

    private Path mockUpServer() throws IOException {
        final Path server = temp.newFolder("server-base").toPath();
        Files.createDirectory(server.resolve(METADATA_DIR));
        final List<ChannelRef> channels = new ArrayList<>();
        channels.add(new ChannelRef("foo:bar", null));
        ProvisioningConfig config = new ProvisioningConfig(channels, new ArrayList<>());
        config.writeConfig(server.resolve(METADATA_DIR).resolve(PROSPERO_CONFIG_FILE_NAME).toFile());
        return server;
    }

    private Path createPatchArchive() throws Exception {
        final DefaultArtifact testArtifact = new DefaultArtifact("foo.bar", "test", null, null, "1.2.3", null, temp.newFile("test-1.2.3.jar"));
        return PatchArchive.createPatchArchive(Collections.singletonList(testArtifact), temp.newFile("patch.zip"), "patch-test00001");
    }


}
