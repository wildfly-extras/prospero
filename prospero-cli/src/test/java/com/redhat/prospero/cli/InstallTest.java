/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InstallTest {

   @Mock
   private Installation installation;

   @Mock
   private CliMain.ActionFactory actionFactory;

   @Test
   public void errorIfTargetPathIsNotPresent() throws Exception {
      try {
         Map<String, String> args = new HashMap<>();
         new Install(actionFactory).handleArgs(args);
         fail("Should have failed");
      } catch (CliMain.ArgumentParsingException e) {
         assertEquals("Target dir argument (--dir) need to be set on install command", e.getMessage());
      }
   }

   @Test
   public void errorIfFplIsNotPresent() throws Exception {
      try {
         Map<String, String> args = new HashMap<>();
         args.put("dir", "test");
         new Install(actionFactory).handleArgs(args);
         fail("Should have failed");
      } catch (CliMain.ArgumentParsingException e) {
         assertEquals("Feature pack name argument (--fpl) need to be set on install command", e.getMessage());
      }
   }

   @Test
   public void errorIfChannelsIsNotPresentAndUsingCustomFplOnInstall() throws Exception {
      try {
         Map<String, String> args = new HashMap<>();
         args.put("dir", "test");
         args.put("fpl", "foo:bar");
         new Install(actionFactory).handleArgs(args);
         fail("Should have failed");
      } catch (CliMain.ArgumentParsingException e) {
         assertEquals("Channel file argument (--channel-file) need to be set when using custom fpl", e.getMessage());
      }
   }

   @Test
   public void callProvisionOnInstallCommandWithCustomFpl() throws Exception {
      when(actionFactory.install(any())).thenReturn(installation);
      String channels = Paths.get(CliMainTest.class.getResource("/channels.json").toURI()).toString();

      Map<String, String> args = new HashMap<>();
      args.put("dir", "test");
      args.put("fpl", "org.jboss.eap:wildfly-ee-galleon-pack");
      args.put("channel-file", channels);
      new Install(actionFactory).handleArgs(args);

      Mockito.verify(actionFactory).install(eq(Paths.get("test").toAbsolutePath()));
      Mockito.verify(installation).provision(eq("org.jboss.eap:wildfly-ee-galleon-pack"), any(List.class));
   }

   @Test
   public void callProvisionOnInstallEapCommand() throws Exception {
      when(actionFactory.install(any())).thenReturn(installation);

      Map<String, String> args = new HashMap<>();
      args.put("dir", "test");
      args.put("fpl", "eap");
      new Install(actionFactory).handleArgs(args);

      Mockito.verify(actionFactory).install(eq(Paths.get("test").toAbsolutePath()));
      Mockito.verify(installation).provision(eq("org.jboss.eap:wildfly-ee-galleon-pack"), any(List.class));
   }

   @Test
   public void callProvisionOnInstallEapOverrideChannelsCommand() throws Exception {
      when(actionFactory.install(any())).thenReturn(installation);
      String channels = Paths.get(CliMainTest.class.getResource("/channels.json").toURI()).toString();

      Map<String, String> args = new HashMap<>();
      args.put("dir", "test");
      args.put("fpl", "eap");
      args.put("channel-file", channels);
      new Install(actionFactory).handleArgs(args);

      Mockito.verify(actionFactory).install(eq(Paths.get("test").toAbsolutePath()));
      ArgumentMatcher<List<ChannelRef>> matcher = channelRefs -> {
         if (channelRefs.size() != 1) return false;
         return  channelRefs.get(0).getName().equals("dev");
      };
      Mockito.verify(installation).provision(eq("org.jboss.eap:wildfly-ee-galleon-pack"), argThat(matcher));
   }

}
