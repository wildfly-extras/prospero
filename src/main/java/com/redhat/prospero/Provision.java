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

package com.redhat.prospero;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import org.apache.commons.io.FileUtils;

public class Provision {

   public static void main(String[] args) throws Exception {
      if (args.length < 2) {
         System.out.println("Not enough parameters. Need to provide WFLY installation and repository folders.");
         return;
      }
      final String base = args[0];
      final String repo = args[1];

      new Provision().provision(Paths.get(base), Paths.get(repo));
   }

   public void provision(Path base, Path repo) throws Exception {
      // go through manifest
      final Manifest manifest = Manifest.parseManifest(base.resolve("manifest.xml"));

      System.out.println("Resolving artifacts from " + repo);
      final Modules modules = new Modules(base);
      // foreach artifact
      manifest.getEntries().forEach(entry -> {
         //  download from maven repo
         // TODO: use proper maven resolution :)
         Path artifactSource = repo.resolve(Paths.get(
                                         entry.aPackage.replace(".", "/"),
                                         entry.name, entry.version, entry.getFileName()));
         if (!artifactSource.toFile().exists()) {
            throw new RuntimeException("Unable to find artifact " + artifactSource);
         }

         //  find in modules
         // TODO: index modules.xml to make it faster instead of walking them every time
            Collection<Path> updates = modules.find(entry);

         if (updates.isEmpty()) {
            throw new RuntimeException("Artifact " + entry.getFileName() + " not found");
         }

         //  drop jar into module folder
         updates.forEach(p-> {
            try {
               FileUtils.copyFile(artifactSource.toFile(), p.getParent().resolve(artifactSource.getFileName()).toFile());
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         });
      });
      System.out.println("Installation provisioned and ready at " + base);
   }
}
