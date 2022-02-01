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
import java.util.Map;

import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.MetadataException;
import com.redhat.prospero.model.XmlException;
import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.UnresolvedMavenArtifactException;

class UpdateArgs {

   private final CliMain.ActionFactory actionFactory;

   public UpdateArgs(CliMain.ActionFactory actionFactory) {
      this.actionFactory = actionFactory;
   }

   public void handleArgs(Map<String, String> parsedArgs) throws ArgumentParsingException {
      String dir = parsedArgs.get("dir");
      if (dir == null || dir.isEmpty()) {
         throw new ArgumentParsingException("Target dir argument (--dir) need to be set on update command");
      }

      final Path targetPath = Paths.get(dir).toAbsolutePath();
      try {
         actionFactory.update(targetPath).doUpdateAll();
      } catch (ArtifactNotFoundException | UnresolvedMavenArtifactException | MetadataException | IOException | ProvisioningException | XmlException e) {
         e.printStackTrace();
      }
   }
}
