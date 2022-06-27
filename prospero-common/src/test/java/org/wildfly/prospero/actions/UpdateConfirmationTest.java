package org.wildfly.prospero.actions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.layout.ProvisioningPlan;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.verification.Times;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

@RunWith(MockitoJUnitRunner.class)
public class UpdateConfirmationTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Mock
    Console console;

    private Path installDir;

    /**
     * Testable implementation of {@link Update} that overrides {@link Update#findUpdates()} and
     * {@link Update#applyFpUpdates(ProvisioningPlan)} methods to not call Galleon.
     */
    private static class UpdateFake extends Update {

        public UpdateFake(Path installDir, MavenSessionManager mavenSessionManager,
                Console console) throws ProvisioningException, OperationException {
            super(installDir, mavenSessionManager, console);
        }

        @Override
        protected Update.UpdateSet findUpdates() {
            ArtifactChange artifactChange = new ArtifactChange(new DefaultArtifact("g", "a", null, "1.0.0"),
                    new DefaultArtifact("g", "a", null, "1.0.1"));
            return new Update.UpdateSet(ProvisioningPlan.builder(), Collections.singletonList(artifactChange));
        }

        @Override
        protected void applyFpUpdates(ProvisioningPlan updates) {
            // no-op
        }
    }

    @Before
    public void setUp() throws Exception {
        // make console to always return false on confirmation request
        Mockito.when(console.confirmUpdates()).thenReturn(false);

        // create mocked installation directory, needed to create Update instance
        Channel channel = new Channel("test", null, null, null, null);
        String channelYaml = ChannelMapper.toYaml(channel);
        installDir = tempDir.newFolder().toPath();
        installDir.resolve(InstallationMetadata.METADATA_DIR).toFile().mkdir();
        Files.writeString(installDir.resolve(InstallationMetadata.METADATA_DIR)
                .resolve(InstallationMetadata.MANIFEST_FILE_NAME), channelYaml);
        Files.writeString(installDir.resolve(InstallationMetadata.METADATA_DIR)
                .resolve(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME), "channels: []\nrepositories: []");

        installDir.resolve(".galleon").toFile().mkdir();
    }

    @Test
    public void testAskForConfirmation() throws Exception {
        Update update = new UpdateFake(installDir, new MavenSessionManager(), console);
        update.doUpdateAll(false);
        Mockito.verify(console, new Times(1)).confirmUpdates();
        Mockito.verify(console, new Times(0)).updatesComplete(); // update should have been denied
    }

    @Test
    public void testConfirmedConfirmation() throws Exception {
        Update update = new UpdateFake(installDir, new MavenSessionManager(), console);
        update.doUpdateAll(true);
        Mockito.verify(console, new Times(0)).confirmUpdates();
        Mockito.verify(console, new Times(1)).updatesComplete(); // update should have been performed
    }
}
