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

import org.assertj.core.api.iterable.ThrowingExtractor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.ChannelChange;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GitStorageTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private Path base;

    private final ChannelManifest manifest = new ChannelManifest("test", "test-id", "", new ArrayList<>());

    @Before
    public void setUp() throws Exception {
        base = folder.newFolder().toPath().resolve(InstallationMetadata.METADATA_DIR);
    }

    @Test
    public void testChangedArtifactVersion() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());

        setArtifact(manifest, "org.test:test:1.2.3");
        gitStorage.record();

        setArtifact(manifest, "org.test:test:1.2.4");
        gitStorage.record();

        final List<SavedState> revisions = gitStorage.getRevisions();
        final SavedState savedState = revisions.get(0);

        final List<ArtifactChange> changes = gitStorage.getArtifactChanges(savedState);
        assertEquals(1, changes.size());
        assertEquals("1.2.3", changes.get(0).getOldVersion().get());
        assertEquals("1.2.4", changes.get(0).getNewVersion().get());
    }

    @Test
    public void testRemovedArtifact() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());

        setArtifact(manifest, "org.test:test:1.2.3");
        gitStorage.record();

        setArtifact(manifest, null);
        ManifestYamlSupport.write(manifest, base.resolve(InstallationMetadata.MANIFEST_FILE_NAME));
        gitStorage.record();

        final List<SavedState> revisions = gitStorage.getRevisions();
        final SavedState savedState = revisions.get(0);

        final List<ArtifactChange> changes = gitStorage.getArtifactChanges(savedState);
        assertEquals(1, changes.size());
        assertEquals("1.2.3", changes.get(0).getOldVersion().get());
        assertTrue(changes.get(0).getNewVersion().isEmpty());
    }

    @Test
    public void testAddedArtifact() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());

        ManifestYamlSupport.write(manifest, base.resolve(InstallationMetadata.MANIFEST_FILE_NAME));
        gitStorage.record();

        setArtifact(manifest, "org.test:test:1.2.3");
        gitStorage.record();

        final List<SavedState> revisions = gitStorage.getRevisions();
        final SavedState savedState = revisions.get(0);

        final List<ArtifactChange> changes = gitStorage.getArtifactChanges(savedState);
        assertEquals(1, changes.size());
        assertTrue(changes.get(0).getOldVersion().isEmpty());
        assertEquals("1.2.3", changes.get(0).getNewVersion().get());
    }

    @Test
    public void initialRecordStoresConfigState() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ManifestYamlSupport.write(manifest, base.resolve(InstallationMetadata.MANIFEST_FILE_NAME));
        new ProsperoConfig(List.of(new Channel("", "", null, null, null, null, null)))
                .writeConfig(base);

        gitStorage.record();

        // TODO: replace with gitStorage API for reading config changes
        HashSet<String> storedPaths = getPathsInCommit();

        assertThat(storedPaths).containsExactlyInAnyOrder("manifest.yaml", InstallationMetadata.INSTALLER_CHANNELS_FILE_NAME);
    }

    @Test
    public void testChangedChannel() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ManifestYamlSupport.write(manifest, base.resolve(InstallationMetadata.MANIFEST_FILE_NAME));
        new ProsperoConfig(List.of(new Channel("channel-1", "old", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null)))
                .writeConfig(base);
        gitStorage.record();

        new ProsperoConfig(List.of(new Channel("channel-1", "new", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar2"),
                null, null)))
                .writeConfig(base);
        gitStorage.recordConfigChange();

        final SavedState savedState = gitStorage.getRevisions().get(0);
        final List<ChannelChange> changes = gitStorage.getChannelChanges(savedState);

        assertThat(changes)
                .map(compareAttr((c)->c.getDescription()))
                .containsExactly("old::new");
    }

    @Test
    public void testAddedChannel() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ManifestYamlSupport.write(manifest, base.resolve(InstallationMetadata.MANIFEST_FILE_NAME));
        final Channel channel1 = new Channel("channel-1", "old", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);
        final Channel channel2 = new Channel("channel-2", "new", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);

        new ProsperoConfig(List.of(channel1))
                .writeConfig(base);
        gitStorage.record();

        new ProsperoConfig(List.of(channel1, channel2))
                .writeConfig(base);
        gitStorage.recordConfigChange();

        final SavedState savedState = gitStorage.getRevisions().get(0);
        final List<ChannelChange> changes = gitStorage.getChannelChanges(savedState);

        assertThat(changes)
                .map(compareAttr((c)->c.getName()))
                .containsExactly("[]::channel-2");
    }

    @Test
    public void testRemovedChannel() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ManifestYamlSupport.write(manifest, base.resolve(InstallationMetadata.MANIFEST_FILE_NAME));
        final Channel channel1 = new Channel("channel-1", "old", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);
        final Channel channel2 = new Channel("channel-2", "new", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);

        new ProsperoConfig(List.of(channel1, channel2))
                .writeConfig(base);
        gitStorage.record();

        new ProsperoConfig(List.of(channel2))
                .writeConfig(base);
        gitStorage.recordConfigChange();

        final SavedState savedState = gitStorage.getRevisions().get(0);
        final List<ChannelChange> changes = gitStorage.getChannelChanges(savedState);

        assertThat(changes)
                .map(compareAttr((c)->c.getName()))
                .containsExactly("channel-1::[]");
    }

    @Test
    public void testNoChangedChannels() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ManifestYamlSupport.write(manifest, base.resolve(InstallationMetadata.MANIFEST_FILE_NAME));
        final Channel channel1 = new Channel("channel-1", "old", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);
        final Channel channel2 = new Channel("channel-2", "new", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);

        new ProsperoConfig(List.of(channel1, channel2))
                .writeConfig(base);
        gitStorage.record();

        new ProsperoConfig(List.of(channel1, channel2))
                .writeConfig(base);
        gitStorage.recordConfigChange();

        final SavedState savedState = gitStorage.getRevisions().get(0);
        final List<ChannelChange> changes = gitStorage.getChannelChanges(savedState);

        assertThat(changes)
                .map(compareAttr((c)->c.getName()))
                .isEmpty();
    }

    private static ThrowingExtractor<ChannelChange, String, RuntimeException> compareAttr(Function<Channel, String> attributeReader) {
        Function<Channel, String> escape = (c)->c==null?"[]": attributeReader.apply(c);
        return c -> escape.apply(c.getOldChannel()) + "::" + escape.apply(c.getNewChannel());
    }

    @Test
    public void initialRecordAdjustTimeForFolderCreationDate() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ManifestYamlSupport.write(manifest, base.resolve(InstallationMetadata.MANIFEST_FILE_NAME));
        new ProsperoConfig(List.of(new Channel("", "", null, null, null, null, null)))
                .writeConfig(base);

        // ensure there's a time gap between creation of the folder and record
        final Instant metadataDirCreationTime = Files.readAttributes(base, BasicFileAttributes.class).creationTime().toInstant();
        while (!metadataDirCreationTime.plusMillis(1000).isBefore(new Date().toInstant())) {
            Thread.sleep(100);
        }

        gitStorage.record();

        assertEquals(metadataDirCreationTime.truncatedTo(ChronoUnit.SECONDS),
                getDateOfLastCommit().toInstant().truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    public void showChangesForInitialCommit() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        setArtifact(manifest, "org.test:test:1.2.3");
        final Channel channel1 = new Channel("channel-1", "old", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);

        new ProsperoConfig(List.of(channel1))
                .writeConfig(base);

        gitStorage.record();

        final List<SavedState> revisions = gitStorage.getRevisions();
        final SavedState savedState = revisions.get(0);

        final List<ArtifactChange> changes = gitStorage.getArtifactChanges(savedState);
        assertEquals(1, changes.size());
        assertEquals(Optional.empty(), changes.get(0).getOldVersion());
        assertEquals("1.2.3", changes.get(0).getNewVersion().get());

        final List<ChannelChange> channelChanges = gitStorage.getChannelChanges(savedState);

        assertThat(channelChanges)
                .map(compareAttr((c)->c.getName()))
                .containsExactly("[]::channel-1");
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

    private Date getDateOfLastCommit() throws IOException, GitAPIException {
        final Git git = Git.open(base.resolve(".git").toFile());
        HashSet<String> paths = new HashSet<>();
        for (RevCommit revCommit : git.log().setMaxCount(1).call()) {
            return revCommit.getAuthorIdent().getWhen();
        }
        return null;
    }


    private void setArtifact(ChannelManifest manifest, String gav) throws IOException {
        if (gav == null) {
            manifest = new ChannelManifest(manifest.getName(), manifest.getId(), manifest.getDescription(), Collections.emptyList());
        } else {
            final String[] splitGav = gav.split(":");
            manifest = new ChannelManifest(manifest.getName(), manifest.getId(), manifest.getDescription(),
                    Arrays.asList(new Stream(splitGav[0], splitGav[1], splitGav[2], null)));
        }
        ManifestYamlSupport.write(manifest, base.resolve("manifest.yaml"));
    }
}
