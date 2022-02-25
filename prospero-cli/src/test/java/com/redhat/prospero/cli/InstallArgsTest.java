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

package com.redhat.prospero.cli;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.redhat.prospero.actions.Installation;
import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.api.ProvisioningDefinition;
import com.redhat.prospero.wfchannel.MavenSessionManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InstallArgsTest {

    @Mock
    private Installation installation;

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
    public void errorIfChannelsIsNotPresentAndUsingCustomFplOnInstall() throws Exception {
        try {
            Map<String, String> args = new HashMap<>();
            args.put(CliMain.TARGET_PATH_ARG, "test");
            args.put(CliMain.FPL_ARG, "foo:bar");
            new InstallCommand(actionFactory).execute(args);
            fail("Should have failed");
        } catch (ArgumentParsingException e) {
            assertEquals("Channel file argument (--channel-file) need to be set when using custom fpl", e.getMessage());
        }
    }

    @Test
    public void callProvisionOnInstallCommandWithCustomFpl() throws Exception {
        when(actionFactory.install(any(), any())).thenReturn(installation);
        String channels = Paths.get(InstallArgsTest.class.getResource("/channels.yaml").toURI()).toString();

        Map<String, String> args = new HashMap<>();
        args.put(CliMain.TARGET_PATH_ARG, "test");
        args.put(CliMain.FPL_ARG, "org.jboss.eap:wildfly-ee-galleon-pack");
        args.put(CliMain.CHANNEL_FILE_ARG, channels);
        new InstallCommand(actionFactory).execute(args);

        Mockito.verify(actionFactory).install(eq(Paths.get("test").toAbsolutePath()), any(MavenSessionManager.class));
        Mockito.verify(installation).provision(serverDefiniton.capture());
        assertEquals("org.jboss.eap:wildfly-ee-galleon-pack", serverDefiniton.getValue().getFpl());
    }

    @Test
    public void callProvisionOnInstallEapCommand() throws Exception {
        when(actionFactory.install(any(), any(MavenSessionManager.class))).thenReturn(installation);

        Map<String, String> args = new HashMap<>();
        args.put(CliMain.TARGET_PATH_ARG, "test");
        args.put(CliMain.FPL_ARG, "eap");
        new InstallCommand(actionFactory).execute(args);

        Mockito.verify(actionFactory).install(eq(Paths.get("test").toAbsolutePath()), any(MavenSessionManager.class));
        Mockito.verify(installation).provision(serverDefiniton.capture());
        assertEquals("org.jboss.eap:wildfly-ee-galleon-pack", serverDefiniton.getValue().getFpl());
    }

    @Test
    public void callProvisionOnInstallEapOverrideChannelsCommand() throws Exception {
        when(actionFactory.install(any(), any(MavenSessionManager.class))).thenReturn(installation);
        String channels = Paths.get(InstallArgsTest.class.getResource("/channels.yaml").toURI()).toString();

        Map<String, String> args = new HashMap<>();
        args.put(CliMain.TARGET_PATH_ARG, "test");
        args.put(CliMain.FPL_ARG, "eap");
        args.put(CliMain.CHANNEL_FILE_ARG, channels);
        new InstallCommand(actionFactory).execute(args);

        Mockito.verify(actionFactory).install(eq(Paths.get("test").toAbsolutePath()), any(MavenSessionManager.class));
        ArgumentMatcher<List<ChannelRef>> matcher = channelRefs -> {
            if (channelRefs.size() != 1) return false;
            return channelRefs.get(0).getName().equals("dev");
        };
        Mockito.verify(installation).provision(serverDefiniton.capture());
        assertEquals("org.jboss.eap:wildfly-ee-galleon-pack", serverDefiniton.getValue().getFpl());
    }

}
