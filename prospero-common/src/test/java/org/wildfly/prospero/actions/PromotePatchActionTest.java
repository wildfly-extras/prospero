package org.wildfly.prospero.actions;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.layout.FeaturePackUpdatePlan;
import org.jboss.galleon.progresstracking.ProgressCallback;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.patch.PatchArchive;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PromotePatchActionTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void channelCoordinateMustHaveGA() throws Exception {
        try {
            new PromotePatchAction(new TestConsole()).promote(createPatchArchive(), new URL("file://test/test-repo"),
                    ChannelRef.fromString("file://test/test.zip"));
            fail("URL Channel GA should not be allowed");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Channel reference has to use Maven GA."));
        }
    }

    @Test
    public void promoteArtifactsFromArchive() throws Exception {
        final PromotePatchAction action = new PromotePatchAction(new TestConsole());
        final Path targetRepo = temp.newFolder().toPath();

        action.promote(createPatchArchive(), targetRepo.toUri().toURL(), ChannelRef.fromString("org.test:test-channel"));

        assertTrue(Files.exists(targetRepo.resolve(Paths.get("foo", "bar", "test", "1.2.3", "test-1.2.3.jar"))));
    }

    private Path createPatchArchive() throws Exception {
        final DefaultArtifact testArtifact = new DefaultArtifact("foo.bar", "test", null, null, "1.2.3", null, temp.newFile("test-1.2.3.jar"));
        return PatchArchive.createPatchArchive(Collections.singletonList(testArtifact), temp.newFile("patch.zip"));
    }

    private class TestConsole implements Console {

        @Override
        public void installationComplete() {

        }

        @Override
        public ProgressCallback<?> getProgressCallback(String id) {
            return null;
        }

        @Override
        public void updatesFound(Collection<FeaturePackUpdatePlan> updates, List<ArtifactChange> changes) {

        }

        @Override
        public boolean confirmUpdates() {
            return false;
        }

        @Override
        public void updatesComplete() {

        }
    }
}