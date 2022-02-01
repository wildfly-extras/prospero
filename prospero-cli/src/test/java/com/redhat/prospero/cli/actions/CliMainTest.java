package com.redhat.prospero.cli.actions;

import java.net.URL;
import java.nio.file.Paths;
import java.util.List;

import com.redhat.prospero.api.ChannelRef;
import integration.TestUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CliMainTest {

   @Mock
   private Installation installation;

   @Mock
   private CliMain.ActionFactory actionFactory;

   @Test
   public void errorIfTargetPathIsNotPresentOnInstall() throws Exception {
      try {
         new CliMain(actionFactory).handleArgs(new String[]{"install"});
         fail("Should have failed");
      } catch (CliMain.ArgumentParsingException e) {
         assertEquals("Target dir argument (--dir) need to be set on install command", e.getMessage());
      }
   }

   @Test
   public void errorIfFplIsNotPresentOnInstall() throws Exception {
      try {
         new CliMain(actionFactory).handleArgs(new String[]{"install", "--dir=test"});
         fail("Should have failed");
      } catch (CliMain.ArgumentParsingException e) {
         assertEquals("Feature pack name argument (--fpl) need to be set on install command", e.getMessage());
      }
   }

   @Test
   public void errorIfArgNameDoesntStartWithDoubleHyphens() throws Exception {
      try {
         new CliMain(actionFactory).handleArgs(new String[]{"install", "dir=test"});
         fail("Should have failed");
      } catch (CliMain.ArgumentParsingException e) {
         assertEquals("Argument [dir=test] not recognized", e.getMessage());
      }
   }

   @Test
   public void errorIfChannelsIsNotPresentAndUsingCustomFplOnInstall() throws Exception {
      try {
         new CliMain(actionFactory).handleArgs(new String[]{"install", "--dir=test", "--fpl=foo:bar"});
         fail("Should have failed");
      } catch (CliMain.ArgumentParsingException e) {
         assertEquals("Channel file argument (--channel-file) need to be set when using custom fpl", e.getMessage());
      }
   }

   @Test
   public void errorIfArgumentHasNoValue() throws Exception {
      try {
         new CliMain(actionFactory).handleArgs(new String[]{"install", "--dir"});
         fail("Should have failed");
      } catch (CliMain.ArgumentParsingException e) {
         assertEquals("Argument value cannot be empty", e.getMessage());
      }
   }

   @Test
   public void errorOnUnknownInstallArgument() throws Exception {
      try {
         new CliMain(actionFactory).handleArgs(new String[]{"install", "--foo=bar"});
         fail("Should have failed");
      } catch (CliMain.ArgumentParsingException e) {
         assertEquals("Argument name [--foo] not recognized", e.getMessage());
      }
   }

   @Test
   public void errorOnUnknownOperation() throws Exception {
      try {
         new CliMain(actionFactory).handleArgs(new String[]{"foo"});
         fail("Should have failed");
      } catch (CliMain.ArgumentParsingException e) {
         assertEquals("Unknown operation foo", e.getMessage());
      }
   }

   @Test
   public void callProvisionOnInstallCommandWithCustomFpl() throws Exception {
      when(actionFactory.install(any())).thenReturn(installation);
      String channels = Paths.get(CliMainTest.class.getResource("/channels.json").toURI()).toString();

      new CliMain(actionFactory).handleArgs(new String[]{"install",
         "--dir=test",
         "--fpl=org.jboss.eap:wildfly-ee-galleon-pack",
         "--channel-file=" + channels});

      Mockito.verify(actionFactory).install(eq(Paths.get("test").toAbsolutePath()));
      Mockito.verify(installation).provision(eq("org.jboss.eap:wildfly-ee-galleon-pack"), any(List.class));
   }

   @Test
   public void callProvisionOnInstallEapCommand() throws Exception {
      when(actionFactory.install(any())).thenReturn(installation);

      new CliMain(actionFactory).handleArgs(new String[]{"install",
         "--dir=test",
         "--fpl=eap"});

      Mockito.verify(actionFactory).install(eq(Paths.get("test").toAbsolutePath()));
      Mockito.verify(installation).provision(eq("org.jboss.eap:wildfly-ee-galleon-pack"), any(List.class));
   }

   @Test
   public void callProvisionOnInstallEapOverrideChannelsCommand() throws Exception {
      when(actionFactory.install(any())).thenReturn(installation);
      String channels = Paths.get(CliMainTest.class.getResource("/channels.json").toURI()).toString();

      new CliMain(actionFactory).handleArgs(new String[]{"install",
         "--dir=test",
         "--fpl=eap",
         "--channel-file=" + channels});

      Mockito.verify(actionFactory).install(eq(Paths.get("test").toAbsolutePath()));
      ArgumentMatcher<List<ChannelRef>> matcher = new ArgumentMatcher<List<ChannelRef>>() {
         @Override
         public boolean matches(List<ChannelRef> channelRefs) {
            if (channelRefs.size() != 1) return false;
            return  channelRefs.get(0).getName().equals("dev");
         }
      };
      Mockito.verify(installation).provision(eq("org.jboss.eap:wildfly-ee-galleon-pack"), argThat(matcher));
   }

}