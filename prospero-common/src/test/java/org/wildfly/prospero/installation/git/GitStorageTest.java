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

import org.apache.commons.io.FileUtils;
import org.assertj.core.api.iterable.ThrowingExtractor;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.xml.ProvisioningXmlWriter;
import org.junit.After;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.api.Diff;
import org.wildfly.prospero.api.FeatureChange;
import org.wildfly.prospero.metadata.ManifestVersionRecord;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.ChannelChange;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Stream;

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
import static org.wildfly.prospero.api.FeatureChange.Type.CONFIG;
import static org.wildfly.prospero.api.FeatureChange.Type.FEATURE;
import static org.wildfly.prospero.api.FeatureChange.Type.LAYERS;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class GitStorageTest {

    protected static final Channel A_CHANNEL = new Channel("channel-1", "old", null,
            List.of(new Repository("test", "http://test.te")),
            new ChannelManifestCoordinate("foo", "bar"),
            null, null);
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private Path base;
    private Path revertPath;

    private final ChannelManifest manifest = new ChannelManifest("test", "test-id", "", new ArrayList<>());

    @Before
    public void setUp() throws Exception {
        base = folder.newFolder().toPath().resolve(ProsperoMetadataUtils.METADATA_DIR);
    }

    @After
    public void tearDown() throws Exception {
        if (revertPath != null) {
            FileUtils.deleteQuietly(revertPath.toFile());
        }
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
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);
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

        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);
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
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);
        generateProsperoConfig(List.of(
                new Channel.Builder().build()));

        gitStorage.record();

        // TODO: replace with gitStorage API for reading config changes
        HashSet<String> storedPaths = getPathsInCommit();

        assertThat(storedPaths).containsExactlyInAnyOrder("manifest.yaml", ProsperoMetadataUtils.INSTALLER_CHANNELS_FILE_NAME);
    }

    private void generateProsperoConfig(List<Channel> channels) throws IOException {
        ProsperoMetadataUtils.writeChannelsConfiguration(base.resolve(ProsperoMetadataUtils.INSTALLER_CHANNELS_FILE_NAME), channels);
    }

    @Test
    public void testChangedChannel() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);
        generateProsperoConfig(List.of(new Channel("channel-1", "old", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null)));
        gitStorage.record();

        generateProsperoConfig(List.of(new Channel("channel-1", "new", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar2"),
                null, null)));
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
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);
        final Channel channel1 = new Channel("channel-1", "old", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);
        final Channel channel2 = new Channel("channel-2", "new", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);

        generateProsperoConfig(List.of(channel1));
        gitStorage.record();

        generateProsperoConfig(List.of(channel1, channel2));
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
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);
        final Channel channel1 = new Channel("channel-1", "old", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);
        final Channel channel2 = new Channel("channel-2", "new", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);

        generateProsperoConfig(List.of(channel1, channel2));
        gitStorage.record();

        generateProsperoConfig(List.of(channel2));
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
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);
        final Channel channel1 = new Channel("channel-1", "old", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);
        final Channel channel2 = new Channel("channel-2", "new", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);

        generateProsperoConfig(List.of(channel1, channel2));
        gitStorage.record();

        generateProsperoConfig(List.of(channel1, channel2));
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
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);
        generateProsperoConfig(List.of(new Channel("", "", null, null, null, null, null)));

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

        generateProsperoConfig(List.of(channel1));

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

    @Test
    public void recordWithAdditionalInformation() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        setArtifact(manifest, "org.test:test:1.2.3");
        final Channel channel1 = new Channel("channel-1", "old", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);

        generateProsperoConfig(List.of(channel1));
        // create the manifest version record to provide version info for commit
        final ManifestVersionRecord record = new ManifestVersionRecord();
        record.addManifest(new ManifestVersionRecord.MavenManifest("foo", "bar", "1.0.0"));
        ProsperoMetadataUtils.writeVersionRecord(base.resolve(ProsperoMetadataUtils.CURRENT_VERSION_FILE), record);

        gitStorage.record();

        assertEquals(SavedState.Type.INSTALL, gitStorage.getRevisions().get(0).getType());
        assertEquals("[foo:bar::1.0.0]", gitStorage.getRevisions().get(0).getMsg());
    }

    @Test
    public void readCommitWithOnlyType() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        setArtifact(manifest, "org.test:test:1.2.3");
        final Channel channel1 = new Channel("channel-1", "old", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null);

        generateProsperoConfig(List.of(channel1));

        gitStorage.record();

        assertEquals(SavedState.Type.INSTALL, gitStorage.getRevisions().get(0).getType());
    }

    @Test
    public void testRevert() throws Exception {
        // record INSTALL and UPDATE
        final GitStorage gitStorage = new GitStorage(base.getParent());

        setArtifact(manifest, "org.test:test:1.2.3");
        ManifestVersionRecord record = new ManifestVersionRecord();
        record.addManifest(new ManifestVersionRecord.MavenManifest("foo", "bar", "1.0.0"));
        ProsperoMetadataUtils.writeVersionRecord(base.resolve(ProsperoMetadataUtils.CURRENT_VERSION_FILE), record);
        gitStorage.record();

        setArtifact(manifest, "org.test:test:1.2.4");
        record = new ManifestVersionRecord();
        record.addManifest(new ManifestVersionRecord.MavenManifest("foo", "bar", "1.0.1"));
        ProsperoMetadataUtils.writeVersionRecord(base.resolve(ProsperoMetadataUtils.CURRENT_VERSION_FILE), record);
        gitStorage.record();

        revertPath = gitStorage.revert(gitStorage.getRevisions().get(1));

        // verify the new folder contains reverted values
        final Path revertedMetadata = revertPath.resolve(ProsperoMetadataUtils.METADATA_DIR);
        assertTrue(ManifestYamlSupport.parse(revertedMetadata.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME).toFile())
                .getStreams().stream()
                    .anyMatch(s->s.getArtifactId().equals("test") && s.getVersion().equals("1.2.3")));
        assertEquals("1.0.0", ManifestVersionRecord.read(revertedMetadata.resolve(ProsperoMetadataUtils.CURRENT_VERSION_FILE))
                .get().getMavenManifests().get(0).getVersion());

        // verify the base folder has not been changed
        assertTrue(ManifestYamlSupport.parse(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME).toFile())
                .getStreams().stream()
                .anyMatch(s->s.getArtifactId().equals("test") && s.getVersion().equals("1.2.4")));
        assertEquals("1.0.1", ManifestVersionRecord.read(base.resolve(ProsperoMetadataUtils.CURRENT_VERSION_FILE))
                .get().getMavenManifests().get(0).getVersion());
    }

    @Test
    public void includeAddedFeaturesInHistory() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);

        generateProsperoConfig(List.of(A_CHANNEL));
        // write provisioning.xml
        ProvisioningConfig config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-two:zip"))
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-three:zip"))
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        final SavedState latestState = gitStorage.getRevisions().get(0);
        final List<FeatureChange> featureChanges = gitStorage.getFeatureChanges(latestState);
        assertThat(featureChanges)
                .containsOnly(
                        new FeatureChange(FEATURE, "org.test:feature-two:zip", Diff.Status.REMOVED),
                        new FeatureChange(FEATURE, "org.test:feature-three:zip", Diff.Status.ADDED));
    }

    @Test
    public void includeChangedLayersInHistory() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);

        generateProsperoConfig(List.of(A_CHANNEL));
        // write provisioning.xml
        ProvisioningConfig config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .addConfig(ConfigModel.builder("model-one", "name-one")
                        .includeLayer("layer-one")
                        .includeLayer("layer-two")
                        .build())
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .addConfig(ConfigModel.builder("model-one", "name-one")
                        .includeLayer("layer-one")
                        .includeLayer("layer-three")
                        .build())
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        final SavedState latestState = gitStorage.getRevisions().get(0);
        final List<FeatureChange> featureChanges = gitStorage.getFeatureChanges(latestState);

        assertThat(featureChanges)
                .containsOnly(
                        new FeatureChange(CONFIG, "model-one:name-one", Diff.Status.MODIFIED,
                                new FeatureChange(LAYERS, "layer-one, layer-two", "layer-one, layer-three"))
                );
    }

    @Test
    public void dontIncludeConfigChangesIfNotModified() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);

        generateProsperoConfig(List.of(A_CHANNEL));
        // write provisioning.xml
        ProvisioningConfig config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .addConfig(ConfigModel.builder("model-one", "name-one")
                        .includeLayer("layer-one")
                        .includeLayer("layer-two")
                        .build())
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .addConfig(ConfigModel.builder("model-one", "name-one")
                        .includeLayer("layer-one")
                        .includeLayer("layer-two")
                        .build())
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        final SavedState latestState = gitStorage.getRevisions().get(0);
        final List<FeatureChange> featureChanges = gitStorage.getFeatureChanges(latestState);

        assertThat(featureChanges)
                .isEmpty();
    }

    @Test
    public void includeAddedConfigChange() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);

        generateProsperoConfig(List.of(A_CHANNEL));
        // write provisioning.xml
        ProvisioningConfig config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .addConfig(ConfigModel.builder("model-one", "name-one")
                        .includeLayer("layer-one")
                        .build())
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        final SavedState latestState = gitStorage.getRevisions().get(0);
        final List<FeatureChange> featureChanges = gitStorage.getFeatureChanges(latestState);

        assertThat(featureChanges)
                .containsOnly(
                        new FeatureChange(CONFIG, "model-one:name-one", Diff.Status.ADDED,
                                new FeatureChange(LAYERS, null, "layer-one"))
                );
    }

    @Test
    public void includeRemovedConfigChange() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);

        generateProsperoConfig(List.of(A_CHANNEL));
        // write provisioning.xml
        ProvisioningConfig config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .addConfig(ConfigModel.builder("model-one", "name-one")
                        .includeLayer("layer-one")
                        .build())
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        final SavedState latestState = gitStorage.getRevisions().get(0);
        final List<FeatureChange> featureChanges = gitStorage.getFeatureChanges(latestState);

        assertThat(featureChanges)
                .containsOnly(
                        new FeatureChange(CONFIG, "model-one:name-one", Diff.Status.REMOVED,
                                new FeatureChange(LAYERS, "layer-one", null))
                );
    }

    @Test
    public void includeAllProvisioningChangesInInstallStage() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);

        generateProsperoConfig(List.of(A_CHANNEL));
        // write provisioning.xml
        ProvisioningConfig config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .addConfig(ConfigModel.builder("model-one", "name-one")
                        .includeLayer("layer-one")
                        .build())
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        final SavedState latestState = gitStorage.getRevisions().get(0);
        final List<FeatureChange> featureChanges = gitStorage.getFeatureChanges(latestState);

        assertThat(featureChanges)
                .containsOnly(
                        new FeatureChange(FEATURE, "org.test:feature-one:zip", Diff.Status.ADDED),
                        new FeatureChange(CONFIG, "model-one:name-one", Diff.Status.ADDED,
                                new FeatureChange(LAYERS, null, "layer-one"))
                );
    }

    @Test
    public void findLatestVersionOfProvisioningRecord_OnRevert() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        setArtifact(manifest, "org.test:test:1.2.3");
        gitStorage.record();

        Files.writeString(base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML), "first");
        gitStorage.record();

        Files.writeString(base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML), "second");
        gitStorage.record();

        final SavedState savedState = gitStorage.getRevisions().get(2);

        final Path reverted = gitStorage.revert(savedState);

        assertThat(reverted.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML))
                .exists()
                .hasContent("first");
    }

    @Test
    public void useRecordedVersionOfProvisioningRecord_OnRevert() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        setArtifact(manifest, "org.test:test:1.2.3");
        gitStorage.record();

        Files.writeString(base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML), "first");
        gitStorage.record();

        Files.writeString(base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML), "second");
        gitStorage.record();

        Files.writeString(base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML), "third");
        gitStorage.record();

        final SavedState savedState = gitStorage.getRevisions().get(1);

        final Path reverted = gitStorage.revert(savedState);

        assertThat(reverted.resolve(ProsperoMetadataUtils.METADATA_DIR).resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML))
                .exists()
                .hasContent("second");
    }

    @Test
    public void storeOnlySelectedFiles() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        setArtifact(manifest, "org.test:test:1.2.3");
        gitStorage.record();

        Files.writeString(base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML), "first");
        setArtifact(manifest, "org.test:test:1.2.4");
        gitStorage.recordChange(SavedState.Type.UPDATE, ProsperoMetadataUtils.PROVISIONING_RECORD_XML);

        final SavedState savedState = gitStorage.getRevisions().get(0);

        assertThat(gitStorage.getArtifactChanges(savedState))
                .isEmpty();
    }

    @Test
    public void getArtifactChangesToCurrent() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());

        setArtifact(manifest, "org.test:test:1.2.3");
        gitStorage.record();

        setArtifact(manifest, "org.test:test:1.2.4");
        gitStorage.record();

        setArtifact(manifest, "org.test:test:1.2.5");
        gitStorage.record();

        final List<SavedState> revisions = gitStorage.getRevisions();
        final SavedState savedState = revisions.get(2);

        final List<ArtifactChange> changes = gitStorage.getArtifactChangesSince(savedState);
        assertEquals(1, changes.size());
        assertEquals("1.2.3", changes.get(0).getOldVersion().get());
        assertEquals("1.2.5", changes.get(0).getNewVersion().get());
    }

    @Test
    public void testChangedChannelToCurrent() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);
        generateProsperoConfig(List.of(new Channel("channel-1", "old", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar"),
                null, null)));
        gitStorage.record();

        generateProsperoConfig(List.of(new Channel("channel-1", "new", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar2"),
                null, null)));
        gitStorage.recordConfigChange();

        generateProsperoConfig(List.of(new Channel("channel-1", "latest", null,
                List.of(new Repository("test", "http://test.te")),
                new ChannelManifestCoordinate("foo", "bar3"),
                null, null)));
        gitStorage.recordConfigChange();

        final SavedState savedState = gitStorage.getRevisions().get(2);
        final List<ChannelChange> changes = gitStorage.getChannelChangesSince(savedState);

        assertThat(changes)
                .map(compareAttr((c)->c.getDescription()))
                .containsExactly("old::latest");
    }

    @Test
    public void includeAddedFeaturesInHistoryToCurrent() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        ProsperoMetadataUtils.writeManifest(base.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), manifest);

        generateProsperoConfig(List.of(A_CHANNEL));
        // write provisioning.xml
        ProvisioningConfig config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-two:zip"))
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-three:zip"))
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        config = ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-one:zip"))
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:feature-four:zip"))
                .build();
        ProvisioningXmlWriter.getInstance().write(config, base.resolve(ProsperoMetadataUtils.PROVISIONING_RECORD_XML));
        gitStorage.record();

        final SavedState latestState = gitStorage.getRevisions().get(2);
        final List<FeatureChange> featureChanges = gitStorage.getFeatureChangesSince(latestState);
        assertThat(featureChanges)
                .containsOnly(
                        new FeatureChange(FEATURE, "org.test:feature-two:zip", Diff.Status.REMOVED),
                        new FeatureChange(FEATURE, "org.test:feature-four:zip", Diff.Status.ADDED));
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
        ProsperoMetadataUtils.writeManifest(base.resolve("manifest.yaml"), manifest);
    }
}
