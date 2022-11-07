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

package org.wildfly.prospero.installation.git;

import org.assertj.core.api.Assertions;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.model.ProsperoConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GitStorageTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private Path base;

    @Before
    public void setUp() throws Exception {
        base = folder.newFolder().toPath().resolve(InstallationMetadata.METADATA_DIR);
    }

    @Test
    public void testChangedArtifactVersion() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        final ChannelManifest manifest = new ChannelManifest("test", "", new ArrayList<>());

        setArtifact(manifest, "org.test:test:1.2.3");
        gitStorage.record();

        setArtifact(manifest, "org.test:test:1.2.4");
        gitStorage.record();

        final List<SavedState> revisions = gitStorage.getRevisions();
        final SavedState savedState = revisions.get(1);

        final List<ArtifactChange> changes = gitStorage.getChanges(savedState);
        assertEquals(1, changes.size());
        assertEquals("1.2.3", changes.get(0).getOldVersion().get());
        assertEquals("1.2.4", changes.get(0).getNewVersion().get());
    }

    @Test
    public void testRemovedArtifact() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        final ChannelManifest manifest = new ChannelManifest("test", "", new ArrayList<>());

        setArtifact(manifest, "org.test:test:1.2.3");
        gitStorage.record();

        setArtifact(manifest, null);
        ManifestYamlSupport.write(manifest, base.resolve(InstallationMetadata.MANIFEST_FILE_NAME));
        gitStorage.record();

        final List<SavedState> revisions = gitStorage.getRevisions();
        final SavedState savedState = revisions.get(1);

        final List<ArtifactChange> changes = gitStorage.getChanges(savedState);
        assertEquals(1, changes.size());
        assertEquals("1.2.3", changes.get(0).getOldVersion().get());
        assertTrue(changes.get(0).getNewVersion().isEmpty());
    }

    @Test
    public void testAddedArtifact() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        final ChannelManifest manifest = new ChannelManifest("test", "", new ArrayList<>());

        ManifestYamlSupport.write(manifest, base.resolve(InstallationMetadata.MANIFEST_FILE_NAME));
        gitStorage.record();

        setArtifact(manifest, "org.test:test:1.2.3");
        gitStorage.record();

        final List<SavedState> revisions = gitStorage.getRevisions();
        final SavedState savedState = revisions.get(1);

        final List<ArtifactChange> changes = gitStorage.getChanges(savedState);
        assertEquals(1, changes.size());
        assertTrue(changes.get(0).getOldVersion().isEmpty());
        assertEquals("1.2.3", changes.get(0).getNewVersion().get());
    }

    @Test
    public void initialRecordStoresConfigState() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        final ChannelManifest manifest = new ChannelManifest("test", "", new ArrayList<>());
        ManifestYamlSupport.write(manifest, base.resolve(InstallationMetadata.MANIFEST_FILE_NAME));
        new ProsperoConfig(List.of(new Channel("", "", null, null, null, null)))
                .writeConfig(base.resolve(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME));

        gitStorage.record();

        // TODO: replace with gitStorage API for reading config changes
        HashSet<String> storedPaths = getPathsInCommit();

        Assertions.assertThat(storedPaths).containsExactlyInAnyOrder("manifest.yaml", InstallationMetadata.PROSPERO_CONFIG_FILE_NAME);
    }

    private HashSet<String> getPathsInCommit() throws IOException, GitAPIException {
        final Git git = Git.open(base.resolve(".git").toFile());
        HashSet<String> paths = new HashSet<>();
        for (RevCommit revCommit : git.log().setMaxCount(1).call()) {
            try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                treeWalk.reset(revCommit.getTree().getId());
                while (treeWalk.next()) {
                    paths.add(treeWalk.getPathString());
                }
            }
        }
        return paths;
    }


    private void setArtifact(ChannelManifest manifest, String gav) throws IOException {
        if (gav == null) {
            manifest = new ChannelManifest(manifest.getName(), manifest.getDescription(), Collections.emptyList());
        } else {
            final String[] splitGav = gav.split(":");
            manifest = new ChannelManifest(manifest.getName(), manifest.getDescription(), Arrays.asList(new Stream(splitGav[0], splitGav[1], splitGav[2], null)));
        }
        ManifestYamlSupport.write(manifest, base.resolve("manifest.yaml"));
    }
}