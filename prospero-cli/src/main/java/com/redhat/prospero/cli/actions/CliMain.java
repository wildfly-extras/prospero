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

package com.redhat.prospero.cli.actions;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.api.MetadataException;
import com.redhat.prospero.api.Server;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.jboss.galleon.ProvisioningException;

public class CliMain {

   private static final Set<String> ALLOWED_ARGUMENTS = new HashSet<>(Arrays.asList("dir", "fpl", "channel-file"));
   private ActionFactory actionFactory;

   public CliMain(ActionFactory actionFactory) {
      this.actionFactory = actionFactory;
   }

   public static void main(String[] args) {
      try {
         new CliMain(new ActionFactory()).handleArgs(args);
      } catch (ArgumentParsingException e) {
         System.out.println(e.getMessage());
         System.exit(1);
      }
   }

   public void handleArgs(String[] args) throws ArgumentParsingException {
      final String operation = args[0];
      if (!("install".equals(operation) || "update".equals(operation))) {
         throw new ArgumentParsingException("Unknown operation " + operation);
      }

      Map<String, String> parsedArgs = parseArguments(args);

      if ("install".equals(operation)) {
         doInstall(parsedArgs);
      }
   }

   private void doInstall(Map<String, String> parsedArgs) throws ArgumentParsingException {
      String dir = parsedArgs.get("dir");
      String fpl = parsedArgs.get("fpl");
      String channelFile = parsedArgs.get("channel-file");

      if (dir == null || dir.isEmpty()) {
         throw new ArgumentParsingException("Target dir argument (--dir) need to be set on install command");
      }
      if (fpl == null || fpl.isEmpty()) {
         throw new ArgumentParsingException("Feature pack name argument (--fpl) need to be set on install command");
      }


      if (!fpl.equals("eap") && !fpl.equals("wildfly") && (channelFile == null || channelFile.isEmpty())) {
         throw new ArgumentParsingException("Channel file argument (--channel-file) need to be set when using custom fpl");
      }

      try {
         final Path installationDir = Paths.get(dir).toAbsolutePath();
         final Server server = new Server(fpl, channelFile==null?null:Paths.get(channelFile).toAbsolutePath());
         final List<ChannelRef> channels = server.getChannelRefs();
         fpl = server.getFpl();

         actionFactory.install(installationDir).provision(fpl, channels);
      } catch (IOException e) {
         e.printStackTrace();
      } catch (ProvisioningException | MetadataException e) {
         e.printStackTrace();
      }
   }

   private Map<String, String> parseArguments(String[] args) throws ArgumentParsingException {
      Map<String, String> parsedArgs = new HashMap<>();
      for (int i = 1; i< args.length; i++) {
         final int nameEndIndex = args[i].indexOf('=');

         if (nameEndIndex < 0 ) {
            throw new ArgumentParsingException("Argument value cannot be empty");
         }

         if (!args[i].startsWith("--")) {
            throw new ArgumentParsingException("Argument [%s] not recognized", args[i]);
         }

         final String name = (nameEndIndex > 0)? args[i].substring(2, nameEndIndex): args[i];
         final String value = args[i].substring(nameEndIndex + 1);

         if (!ALLOWED_ARGUMENTS.contains(name)) {
            throw new ArgumentParsingException("Argument name [--%s] not recognized", name);
         }

         if (value.isEmpty()) {
            throw new ArgumentParsingException("Argument value cannot be empty");
         }

         parsedArgs.put(name, value);
      }
      return parsedArgs;
   }

   public static class ActionFactory {
      public Installation install(Path targetPath) {
         return new Installation(targetPath);
      }
   }

   public static class ArgumentParsingException extends Exception {
      public ArgumentParsingException(String msg, Exception e) {
         super(msg, e);
      }

      public ArgumentParsingException(String msg) {
         super(msg);
      }

      public ArgumentParsingException(String msg, String... args) {
         super(String.format(msg, args));
      }
   }

}
