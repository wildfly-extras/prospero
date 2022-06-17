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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.Provision;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProvisioningConfig;
import org.wildfly.prospero.model.RepositoryRef;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class InstallCommandTest extends AbstractConsoleTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    private Provision provisionAction;

    @Captor
    private ArgumentCaptor<ProvisioningDefinition> serverDefiniton;

    @Override
    protected ActionFactory createActionFactory() {
        return new ActionFactory() {
            @Override
            public Provision install(Path targetPath, MavenSessionManager mavenSessionManager, Console console) {
                return provisionAction;
            }
        };
    }

    @Test
    public void errorIfTargetPathIsNotPresent() {
        int exitCode = commandLine.execute("install");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains("Missing required option: '--dir=<directory>'"));
    }

    @Test
    public void errorIfFplIsNotPresent() {
        int exitCode = commandLine.execute("install", "--dir", "test");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains("Missing required argument (specify one of these): (--fpl=<fpl> | --definition=<definition>)"));
    }

    @Test
    public void offlineModeRequiresLocalRepoOption() {
        int exitCode = commandLine.execute("install", "--dir", "test", "--fpl", "eap", "--offline");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(Messages.offlineModeRequiresLocalRepo()));
    }

    @Test
    public void errorIfChannelsIsNotPresentAndUsingCustomFplOnInstall() {
        int exitCode = commandLine.execute("install", "--dir", "test", "--fpl", "foo:bar");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue("output: " + getErrorOutput(), getErrorOutput().contains("Channel file argument (--provision-config) need to be set when using custom fpl"));
    }

    @Test
    public void callProvisionOnInstallCommandWithCustomFpl() throws Exception {
        List<ChannelRef> channels = new ArrayList<>();
        List<RepositoryRef> repositories = new ArrayList<>();
        final File channelsFile = temporaryFolder.newFile();
        new ProvisioningConfig(channels, repositories).writeConfig(channelsFile);

        int exitCode = commandLine.execute("install", "--dir", "test",
                "--fpl", "org.wildfly:wildfly-ee-galleon-pack",
                "--provision-config", channelsFile.getAbsolutePath());
        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(provisionAction).provision(serverDefiniton.capture());
        assertEquals("org.wildfly:wildfly-ee-galleon-pack", serverDefiniton.getValue().getFpl());
    }

    @Test
    public void callProvisionOnInstallEapCommand() throws Exception {
        int exitCode = commandLine.execute("install", "--dir", "test", "--fpl", "eap");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(provisionAction).provision(serverDefiniton.capture());
        assertEquals("org.jboss.eap:wildfly-ee-galleon-pack", serverDefiniton.getValue().getFpl());
    }

    @Test
    public void callProvisionOnInstallEapOverrideChannelsCommand() throws Exception {
        List<ChannelRef> channels = Arrays.asList(new ChannelRef("org.wildfly:wildfly-channel", null));
        List<RepositoryRef> repositories = Arrays.asList(new RepositoryRef("dev", "http://test.test"));
        final File channelsFile = temporaryFolder.newFile();
        new ProvisioningConfig(channels, repositories).writeConfig(channelsFile);

        int exitCode = commandLine.execute("install", "--dir", "test", "--fpl", "eap",
                "--provision-config", channelsFile.getAbsolutePath());

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(provisionAction).provision(serverDefiniton.capture());
        assertEquals("org.jboss.eap:wildfly-ee-galleon-pack", serverDefiniton.getValue().getFpl());
        assertEquals("dev", serverDefiniton.getValue().getRepositories().get(0).getId());
    }

    @Test
    public void usingProvisionDefinitonRequiresChannel() throws Exception {
        List<ChannelRef> channels = Arrays.asList(new ChannelRef("org.wildfly:wildfly-channel", null));
        List<RepositoryRef> repositories = Arrays.asList(new RepositoryRef("dev", "http://test.test"));
        final File provisionDefinitionFile = temporaryFolder.newFile("provision.xml");
        final File channelsFile = temporaryFolder.newFile();
        new ProvisioningConfig(channels, repositories).writeConfig(channelsFile);

        int exitCode = commandLine.execute("install", "--dir", "test",
                "--provision-config", channelsFile.getAbsolutePath(),
                "--definition", provisionDefinitionFile.getAbsolutePath());

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(provisionAction).provision(serverDefiniton.capture());
        assertNull("org.wildfly:wildfly-ee-galleon-pack", serverDefiniton.getValue().getFpl());
        assertEquals("dev", serverDefiniton.getValue().getRepositories().get(0).getId());
        assertEquals(provisionDefinitionFile.toPath(), serverDefiniton.getValue().getDefinition());
    }

    @Test
    public void fplAndDefinitionAreNotAllowedTogether() throws Exception {
        final File provisionDefinitionFile = temporaryFolder.newFile("provision.xml");
        final File channelsFile = temporaryFolder.newFile();

        int exitCode = commandLine.execute("install", "--dir", "test",
                "--definition", provisionDefinitionFile.getAbsolutePath(),
                "--provision-config", channelsFile.getAbsolutePath(),
                "--fpl", "test");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
    }
}
