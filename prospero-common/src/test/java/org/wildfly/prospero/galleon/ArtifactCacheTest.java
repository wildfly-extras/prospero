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

package org.wildfly.prospero.galleon;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.MavenArtifact;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ArtifactCacheTest {

    private static final String GROUP_ID = "group";
    private static final String ARTIFACT_ID = "artifact";
    private static final String EXTENSION = "jar";
    private static final String CLASSIFIER = "classifier";
    private static final String VERSION = "1.0.0";
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private MavenArtifact anArtifact;
    private MavenArtifact otherArtifact;
    private Path installationDir;
    private ArtifactCache cache;

    @Before
    public void setUp() throws Exception {
        anArtifact = new MavenArtifact(GROUP_ID, ARTIFACT_ID, EXTENSION, CLASSIFIER, VERSION, temp.newFile("test.jar"));
        otherArtifact = new MavenArtifact(GROUP_ID + "Two", ARTIFACT_ID, EXTENSION, CLASSIFIER, VERSION, temp.newFile("testTwo.jar"));
        installationDir = temp.newFolder().toPath();
        Files.createDirectories(installationDir.resolve(ArtifactCache.CACHE_FOLDER));
        cache = ArtifactCache.getInstance(installationDir);
    }

    @Test
    public void testNoCacheFolderReturnsNoArtifacts() throws Exception {
        final ArtifactCache cache = ArtifactCache.getInstance(temp.newFolder().toPath());

        assertEquals(Optional.empty(),cache.getArtifact(GROUP_ID, ARTIFACT_ID, EXTENSION, CLASSIFIER, VERSION));
    }

    @Test
    public void testReadBadlyFormattedFile() throws Exception {
        Path newFolder = temp.newFolder().toPath();
        Files.createDirectories(newFolder.resolve(ArtifactCache.CACHE_FOLDER));
        Files.writeString(newFolder.resolve(ArtifactCache.CACHE_FOLDER).resolve(ArtifactCache.CACHE_FILENAME),"badformat");

        assertThatThrownBy(() -> ArtifactCache.getInstance(newFolder))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("PRSP000264")
                .hasStackTraceContaining("Not enough segments");
    }

    @Test
    public void emptyCacheListReturnsNoArtifacts() throws Exception {
        assertEquals(Optional.empty(),cache.getArtifact(GROUP_ID, ARTIFACT_ID, EXTENSION, CLASSIFIER, VERSION));
    }

    @Test
    public void recordCreatesCacheListIfNotPresent() throws Exception {
        cache.record(anArtifact, installationDir.resolve("target.jar"));

        final String line = Files.readString(installationDir.resolve(ArtifactCache.CACHE_FOLDER).resolve(ArtifactCache.CACHE_FILENAME));
        assertThat(line)
                .contains(anArtifact.getGroupId() + ":" + anArtifact.getArtifactId())
                .contains("target.jar")
                .doesNotContain("/target.jar"); // should be relative to installationDir
    }

    @Test
    public void recordReplacesArtifactIfAlreadyAdded() throws Exception {
        cache.record(anArtifact, installationDir.resolve("target.jar"));
        cache.record(otherArtifact, installationDir.resolve("target2.jar"));
        cache.record(anArtifact, installationDir.resolve("target3.jar"));

        final List<String> lines = Files.readAllLines(installationDir.resolve(ArtifactCache.CACHE_FOLDER).resolve(ArtifactCache.CACHE_FILENAME));
        assertEquals(2, lines.size());
        assertThat(lines)
                .allMatch(l->l.startsWith(GROUP_ID + ":" + ARTIFACT_ID) || l.startsWith(GROUP_ID + "Two" + ":" + ARTIFACT_ID))
                .allMatch(l -> l.contains("target2.jar") || l.contains("target3.jar"))
                .doesNotHaveDuplicates();
    }

    @Test
    public void cacheAddsArtifactToCacheFolderAndRecordsIt() throws Exception {
        cache.cache(anArtifact);

        final List<String> line = Files.readAllLines(installationDir.resolve(ArtifactCache.CACHE_FOLDER).resolve(ArtifactCache.CACHE_FILENAME));
        assertEquals(1, line.size());
        final String expectedPath = ArtifactCache.CACHE_FOLDER.resolve(anArtifact.getFile().getName())
                .toString().replace(File.separatorChar, '/');
        assertThat(line.get(0))
                .contains(GROUP_ID + ":" + ARTIFACT_ID)
                .contains(expectedPath);
        assertThat(installationDir.resolve(ArtifactCache.CACHE_FOLDER).resolve(anArtifact.getFile().getName()))
                .hasSameBinaryContentAs(anArtifact.getFile().toPath());
    }

    @Test
    public void getArtifactReturnsFileIfItMatches() throws Exception {
        cache.cache(anArtifact);

        final Optional<File> cachedArtifact = cache.getArtifact(GROUP_ID, ARTIFACT_ID, EXTENSION, CLASSIFIER, VERSION);

        assertTrue(cachedArtifact.isPresent());
        assertThat(cachedArtifact.get())
                .hasSameBinaryContentAs(anArtifact.getFile());
    }

    @Test
    public void getArtifactDoesntReturnArtifactIfTheVersionIsDifferent() throws Exception {
        cache.cache(anArtifact);

        final Optional<File> cachedArtifact = cache.getArtifact(GROUP_ID, ARTIFACT_ID, EXTENSION, CLASSIFIER, "2.0.0");

        assertEquals(Optional.empty(), cachedArtifact);
    }

    @Test
    public void getArtifactDoesntReturnArtifactIfTheHashIsDifferent() throws Exception {
        cache.cache(anArtifact);
        Files.writeString(installationDir.resolve(ArtifactCache.CACHE_FOLDER).resolve(anArtifact.getFile().getName()), "newchange");

        final Optional<File> cachedArtifact = cache.getArtifact(GROUP_ID, ARTIFACT_ID, EXTENSION, CLASSIFIER, VERSION);

        assertEquals(Optional.empty(), cachedArtifact);
    }
}