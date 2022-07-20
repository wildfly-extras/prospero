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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.ArtifactCoordinate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PatchArchiveTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void createAndExtractPatchBundle() throws Exception {
        // create archive
        final Path patchArchive = createPatchArchive();

        // install
        try (final PatchArchive patchFile = PatchArchive.extract(patchArchive)) {
            // should have .patches/channels with channel file and .patches/repository with the repository content
            assertThat(patchFile.getArtifactList()).containsOnly(
                    new ArtifactCoordinate("foo.bar", "test", "", "", "1.2.3")
            );
            assertTrue(Files.exists(patchFile.getRepository().resolve(Paths.get("foo/bar/test/1.2.3/test-1.2.3.jar"))));
        }
    }

    @Test
    public void removeTemporaryFolderOnClose() throws Exception {
        final Path patchArchive = createPatchArchive();

        final Path parent;
        try (final PatchArchive patchFile = PatchArchive.extract(patchArchive)) {
            parent = patchFile.getRepository().getParent();
        }
        assertFalse(Files.exists(parent));
    }

    @Test(expected = IllegalArgumentException.class)
    public void createArchiveWithNoArtifacts() throws Exception {
        PatchArchive.createPatchArchive(Collections.emptyList(), temp.newFile("patch.zip"));
    }

    // TODO: createArchiveWithArtifactWithoutFile

    private Path createPatchArchive() throws Exception {
        final DefaultArtifact testArtifact = new DefaultArtifact("foo.bar", "test", null, null, "1.2.3", null, temp.newFile("test-1.2.3.jar"));
        return PatchArchive.createPatchArchive(Collections.singletonList(testArtifact), temp.newFile("patch.zip"));
    }


}
