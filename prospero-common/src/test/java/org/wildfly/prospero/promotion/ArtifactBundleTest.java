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

package org.wildfly.prospero.promotion;

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

public class ArtifactBundleTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void createAndExtractCustomizationBundle() throws Exception {
        // create archive
        final Path archiveFile = createCustomizationArchive();

        // install
        try (final ArtifactBundle archive = ArtifactBundle.extract(archiveFile)) {
            assertThat(archive.getArtifactList()).containsOnly(
                    new ArtifactCoordinate("foo.bar", "test", "", "", "1.2.3")
            );
            assertTrue(Files.exists(archive.getRepository().resolve(Paths.get("foo/bar/test/1.2.3/test-1.2.3.jar"))));
        }
    }

    @Test
    public void removeTemporaryFolderOnClose() throws Exception {
        final Path archiveFile = createCustomizationArchive();

        final Path parent;
        try (final ArtifactBundle archive = ArtifactBundle.extract(archiveFile)) {
            parent = archive.getRepository().getParent();
        }
        assertFalse(Files.exists(parent));
    }

    @Test(expected = IllegalArgumentException.class)
    public void createArchiveWithNoArtifacts() throws Exception {
        ArtifactBundle.createCustomizationArchive(Collections.emptyList(), temp.newFile("archive.zip"));
    }

    // TODO: createArchiveWithArtifactWithoutFile

    private Path createCustomizationArchive() throws Exception {
        final DefaultArtifact testArtifact = new DefaultArtifact("foo.bar", "test", null, null, "1.2.3", null, temp.newFile("test-1.2.3.jar"));
        return ArtifactBundle.createCustomizationArchive(Collections.singletonList(testArtifact), temp.newFile("archive.zip"));
    }


}
