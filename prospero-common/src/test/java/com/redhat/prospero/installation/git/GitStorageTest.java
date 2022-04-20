package com.redhat.prospero.installation.git;

import com.redhat.prospero.api.ArtifactChange;
import com.redhat.prospero.api.SavedState;
import com.redhat.prospero.model.ManifestYamlSupport;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Stream;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class GitStorageTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    private Path base;

    @Before
    public void setUp() throws Exception {
        base = folder.newFolder().toPath().resolve(".installation");
    }

    @Test
    public void testChangedArtifactVersion() throws Exception {
        final GitStorage gitStorage = new GitStorage(base.getParent());
        final Channel channel = new Channel("test", "", null, null,
                new ArrayList<>());
        final Channel manifest = channel;

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
        final Channel channel = new Channel("test", "", null, null,
                new ArrayList<>());
        final Channel manifest = channel;

        setArtifact(manifest, "org.test:test:1.2.3");
        gitStorage.record();

        setArtifact(manifest, null);
        ManifestYamlSupport.write(channel, base.resolve("manifest.yaml"));
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
        final Channel channel = new Channel("test", "", null, null,
                new ArrayList<>());
        final Channel manifest = channel;

        ManifestYamlSupport.write(channel, base.resolve("manifest.yaml"));
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


    private void setArtifact(Channel manifest, String gav) throws IOException {
        final Channel channel;
        if (gav == null) {
            channel = new Channel("test", "", null, null,
                    Collections.emptyList());
        } else {
            final String[] splitGav = gav.split(":");
            channel = new Channel("test", "", null, null,
                    Arrays.asList(new Stream(splitGav[0], splitGav[1], splitGav[2], null)));
        }
        ManifestYamlSupport.write(channel, base.resolve("manifest.yaml"));
    }
}