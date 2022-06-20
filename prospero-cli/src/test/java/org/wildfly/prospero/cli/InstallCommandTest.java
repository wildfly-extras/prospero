/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.cli;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.wildfly.prospero.actions.Provision;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.model.ProvisioningConfig;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InstallCommandTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    private Provision provisionAction;

    @Mock
    private CliMain.ActionFactory actionFactory;

    @Captor
    private ArgumentCaptor<ProvisioningDefinition> serverDefiniton;

    @Test
    public void errorIfTargetPathIsNotPresent() throws Exception {
        try {
            Map<String, String> args = new HashMap<>();
            new InstallCommand(actionFactory).execute(args);
            fail("Should have failed");
        } catch (ArgumentParsingException e) {
            assertEquals("Target dir argument (--dir) need to be set on install command", e.getMessage());
        }
    }

    @Test
    public void errorIfFplIsNotPresent() throws Exception {
        try {
            Map<String, String> args = new HashMap<>();
            args.put(CliMain.TARGET_PATH_ARG, "test");
            new InstallCommand(actionFactory).execute(args);
            fail("Should have failed");
        } catch (ArgumentParsingException e) {
            assertEquals("Feature pack name argument (--fpl) need to be set on install command", e.getMessage());
        }
    }

    @Test
    public void offlineModeRequiresLocalRepoOption() throws Exception {
        try {
            Map<String, String> args = new HashMap<>();
            args.put(CliMain.TARGET_PATH_ARG, "test");
            args.put(CliMain.FPL_ARG, "eap");
            args.put(CliMain.OFFLINE, "true");
            new InstallCommand(actionFactory).execute(args);
            fail("Should have failed");
        } catch (ArgumentParsingException e) {
            assertEquals(Messages.offlineModeRequiresLocalRepo(), e.getMessage());
        }
    }

    @Test
    public void errorIfChannelsIsNotPresentAndUsingCustomFplOnInstall() throws Exception {
        try {
            Map<String, String> args = new HashMap<>();
            args.put(CliMain.TARGET_PATH_ARG, "test");
            args.put(CliMain.FPL_ARG, "foo:bar");
            new InstallCommand(actionFactory).execute(args);
            fail("Should have failed");
        } catch (ArgumentParsingException e) {
            assertEquals("Channel file argument (--provision-config) need to be set when using custom fpl", e.getMessage());
        }
    }

    @Test
    public void callProvisionOnInstallCommandWithCustomFpl() throws Exception {
        when(actionFactory.install(any(), any())).thenReturn(provisionAction);
        List<ChannelRef> channels = new ArrayList<>();
        List<RepositoryRef> repositories = new ArrayList<>();

        final File channelsFile = temporaryFolder.newFile();
        new ProvisioningConfig(channels, repositories).writeConfig(channelsFile);

        Map<String, String> args = new HashMap<>();
        args.put(CliMain.TARGET_PATH_ARG, "test");
        args.put(CliMain.FPL_ARG, "org.wildfly:wildfly-ee-galleon-pack");
        args.put(CliMain.PROVISION_CONFIG_ARG, channelsFile.getAbsolutePath());
        new InstallCommand(actionFactory).execute(args);

        Mockito.verify(actionFactory).install(eq(Paths.get("test").toAbsolutePath()), any(MavenSessionManager.class));
        Mockito.verify(provisionAction).provision(serverDefiniton.capture());
        assertEquals("org.wildfly:wildfly-ee-galleon-pack", serverDefiniton.getValue().getFpl());
    }

    @Test
    public void callProvisionOnInstallEapCommand() throws Exception {
        when(actionFactory.install(any(), any(MavenSessionManager.class))).thenReturn(provisionAction);

        Map<String, String> args = new HashMap<>();
        args.put(CliMain.TARGET_PATH_ARG, "test");
        args.put(CliMain.FPL_ARG, "eap");
        new InstallCommand(actionFactory).execute(args);

        Mockito.verify(actionFactory).install(eq(Paths.get("test").toAbsolutePath()), any(MavenSessionManager.class));
        Mockito.verify(provisionAction).provision(serverDefiniton.capture());
        assertEquals("org.jboss.eap:wildfly-ee-galleon-pack", serverDefiniton.getValue().getFpl());
    }

    @Test
    public void callProvisionOnInstallEapOverrideChannelsCommand() throws Exception {
        when(actionFactory.install(any(), any(MavenSessionManager.class))).thenReturn(provisionAction);
        List<ChannelRef> channels = Arrays.asList(new ChannelRef("org.wildfly:wildfly-channel", null));
        List<RepositoryRef> repositories = Arrays.asList(new RepositoryRef("dev", "http://test.test"));

        final File channelsFile = temporaryFolder.newFile();
        new ProvisioningConfig(channels, repositories).writeConfig(channelsFile);

        Map<String, String> args = new HashMap<>();
        args.put(CliMain.TARGET_PATH_ARG, "test");
        args.put(CliMain.FPL_ARG, "eap");
        args.put(CliMain.PROVISION_CONFIG_ARG, channelsFile.getAbsolutePath());
        new InstallCommand(actionFactory).execute(args);

        Mockito.verify(actionFactory).install(eq(Paths.get("test").toAbsolutePath()), any(MavenSessionManager.class));
        Mockito.verify(provisionAction).provision(serverDefiniton.capture());
        assertEquals("org.jboss.eap:wildfly-ee-galleon-pack", serverDefiniton.getValue().getFpl());
        assertEquals("dev", serverDefiniton.getValue().getRepositories().get(0).getId());
    }

    @Test
    public void usingProvisionDefinitonRequiresChannel() throws Exception {
        when(actionFactory.install(any(), any(MavenSessionManager.class))).thenReturn(provisionAction);
        List<ChannelRef> channels = Arrays.asList(new ChannelRef("org.wildfly:wildfly-channel", null));
        List<RepositoryRef> repositories = Arrays.asList(new RepositoryRef("dev", "http://test.test"));

        final File provisionDefinitionFile = temporaryFolder.newFile("provision.xml");
        final File channelsFile = temporaryFolder.newFile();
        new ProvisioningConfig(channels, repositories).writeConfig(channelsFile);

        Map<String, String> args = new HashMap<>();
        args.put(CliMain.PROVISION_CONFIG_ARG, channelsFile.getAbsolutePath());
        args.put(CliMain.TARGET_PATH_ARG, "test");
        args.put(InstallCommand.DEFINITION_ARG, provisionDefinitionFile.getAbsolutePath());
        new InstallCommand(actionFactory).execute(args);

        Mockito.verify(actionFactory).install(eq(Paths.get("test").toAbsolutePath()), any(MavenSessionManager.class));
        Mockito.verify(provisionAction).provision(serverDefiniton.capture());
        assertNull("org.wildfly:wildfly-ee-galleon-pack", serverDefiniton.getValue().getFpl());
        assertEquals("dev", serverDefiniton.getValue().getRepositories().get(0).getId());
        assertEquals(provisionDefinitionFile.toPath(), serverDefiniton.getValue().getDefinition());
    }

    @Test(expected = ArgumentParsingException.class)
    public void fplAndDefinitionAreNotAllowedTogether() throws Exception {
        final File provisionDefinitionFile = temporaryFolder.newFile("provision.xml");
        final File channelsFile = temporaryFolder.newFile();

        Map<String, String> args = new HashMap<>();
        args.put(CliMain.PROVISION_CONFIG_ARG, channelsFile.getAbsolutePath());
        args.put(CliMain.TARGET_PATH_ARG, "test");
        args.put(CliMain.FPL_ARG, "test");
        args.put(InstallCommand.DEFINITION_ARG, provisionDefinitionFile.getAbsolutePath());
        new InstallCommand(actionFactory).execute(args);
    }
}
