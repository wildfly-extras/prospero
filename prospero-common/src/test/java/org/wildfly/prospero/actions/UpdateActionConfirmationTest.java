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
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

@RunWith(MockitoJUnitRunner.class)
public class UpdateActionConfirmationTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Mock
    Console console;

    private Path installDir;

    /**
     * Testable implementation of {@link UpdateAction} that overrides {@link UpdateAction#findUpdates()} and
     * {@link UpdateAction#applyFpUpdates(ProvisioningPlan)} methods to not call Galleon.
     */
    private static class UpdateActionFake extends UpdateAction {

        public UpdateActionFake(Path installDir, MavenSessionManager mavenSessionManager,
                                Console console) throws ProvisioningException, OperationException {
            super(installDir, mavenSessionManager, console);
        }

        @Override
        protected UpdateSet findUpdates() {
            ArtifactChange artifactChange = new ArtifactChange(new DefaultArtifact("g", "a", null, "1.0.0"),
                    new DefaultArtifact("g", "a", null, "1.0.1"));
            return new UpdateSet(ProvisioningPlan.builder(), Collections.singletonList(artifactChange));
        }

        @Override
        protected void applyUpdates() {
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
        UpdateAction updateAction = new UpdateActionFake(installDir, new MavenSessionManager(), console);
        updateAction.doUpdateAll(false);
        Mockito.verify(console, new Times(1)).confirmUpdates();
        Mockito.verify(console, new Times(0)).updatesComplete(); // update should have been denied
    }

    @Test
    public void testConfirmedConfirmation() throws Exception {
        UpdateAction updateAction = new UpdateActionFake(installDir, new MavenSessionManager(), console);
        updateAction.doUpdateAll(true);
        Mockito.verify(console, new Times(0)).confirmUpdates();
        Mockito.verify(console, new Times(1)).updatesComplete(); // update should have been performed
    }
}
