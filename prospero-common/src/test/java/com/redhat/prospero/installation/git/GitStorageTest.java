package com.redhat.prospero.installation.git;

import com.redhat.prospero.api.ArtifactChange;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.api.SavedState;
import com.redhat.prospero.model.ManifestYamlSupport;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class GitStorageTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testChangedArtifactVersion() throws Exception {
        final Path base = folder.newFolder().toPath().resolve(".installation");
        final GitStorage gitStorage = new GitStorage(base.getParent());
        List<Artifact> artifacts = new ArrayList<>();
        final Manifest manifest = new Manifest(artifacts, base.resolve("manifest.yaml"));

        artifacts.add(new DefaultArtifact("org.test:test:1.2.3"));
        ManifestYamlSupport.write(manifest);
        gitStorage.record();

        artifacts.remove(0);
        artifacts.add(new DefaultArtifact("org.test:test:1.2.4"));
        ManifestYamlSupport.write(manifest);
        gitStorage.record();

        final List<SavedState> revisions = gitStorage.getRevisions();
        final SavedState savedState = revisions.get(1);

        final List<ArtifactChange> changes = gitStorage.getChanges(savedState);
        assertEquals(1, changes.size());
        assertEquals("1.2.3", changes.get(0).getOldVersion().getVersion());
        assertEquals("1.2.4", changes.get(0).getNewVersion().getVersion());
    }

    @Test
    public void testRemovedArtifact() throws Exception {
        final Path base = folder.newFolder().toPath().resolve(".installation");
        final GitStorage gitStorage = new GitStorage(base.getParent());
        List<Artifact> artifacts = new ArrayList<>();
        final Manifest manifest = new Manifest(artifacts, base.resolve("manifest.yaml"));

        artifacts.add(new DefaultArtifact("org.test:test:1.2.3"));
        ManifestYamlSupport.write(manifest);
        gitStorage.record();

        artifacts.remove(0);
        ManifestYamlSupport.write(manifest);
        gitStorage.record();

        final List<SavedState> revisions = gitStorage.getRevisions();
        final SavedState savedState = revisions.get(1);

        final List<ArtifactChange> changes = gitStorage.getChanges(savedState);
        assertEquals(1, changes.size());
        assertEquals("1.2.3", changes.get(0).getOldVersion().getVersion());
        assertNull(changes.get(0).getNewVersion());
    }

    @Test
    public void testAddedArtifact() throws Exception {
        final Path base = folder.newFolder().toPath().resolve(".installation");
        final GitStorage gitStorage = new GitStorage(base.getParent());
        List<Artifact> artifacts = new ArrayList<>();
        final Manifest manifest = new Manifest(artifacts, base.resolve("manifest.yaml"));

        ManifestYamlSupport.write(manifest);
        gitStorage.record();

        artifacts.add(new DefaultArtifact("org.test:test:1.2.3"));
        ManifestYamlSupport.write(manifest);
        gitStorage.record();

        final List<SavedState> revisions = gitStorage.getRevisions();
        final SavedState savedState = revisions.get(1);

        final List<ArtifactChange> changes = gitStorage.getChanges(savedState);
        assertEquals(1, changes.size());
        assertNull(changes.get(0).getOldVersion());
        assertEquals("1.2.3", changes.get(0).getNewVersion().getVersion());
    }

}