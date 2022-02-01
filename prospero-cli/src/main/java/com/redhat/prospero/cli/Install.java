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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import com.redhat.prospero.api.ChannelRef;
import com.redhat.prospero.api.MetadataException;
import com.redhat.prospero.api.Server;
import org.jboss.galleon.ProvisioningException;

class Install {
   private final CliMain.ActionFactory actionFactory;

   Install(CliMain.ActionFactory actionFactory) {
      this.actionFactory = actionFactory;
   }

   void handleArgs(Map<String, String> parsedArgs) throws CliMain.ArgumentParsingException {
      String dir = parsedArgs.get("dir");
      String fpl = parsedArgs.get("fpl");
      String channelFile = parsedArgs.get("channel-file");

      if (dir == null || dir.isEmpty()) {
         throw new CliMain.ArgumentParsingException("Target dir argument (--dir) need to be set on install command");
      }
      if (fpl == null || fpl.isEmpty()) {
         throw new CliMain.ArgumentParsingException("Feature pack name argument (--fpl) need to be set on install command");
      }


      if (!fpl.equals("eap") && !fpl.equals("wildfly") && (channelFile == null || channelFile.isEmpty())) {
         throw new CliMain.ArgumentParsingException("Channel file argument (--channel-file) need to be set when using custom fpl");
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
}
