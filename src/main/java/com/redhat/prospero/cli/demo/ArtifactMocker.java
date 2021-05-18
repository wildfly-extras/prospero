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

package com.redhat.prospero.cli.demo;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.redhat.prospero.api.Artifact;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.cli.actions.DeployArtifact;
import com.redhat.prospero.impl.repository.LocalRepository;

public class ArtifactMocker {

   public static void main(String[] args) throws Exception {
      String name = args[0];
      String newVersion = args[1];
      String base = args[2];
      String repo = args[3];
      List<String> deps = new ArrayList<>();

      if (args.length > 4) {
         for (int i=4; i< args.length; i++) {
            deps.add(args[i]);
         }
      }

      new ArtifactMocker().mockArtifact(name, newVersion, base, repo, deps);
   }

   public void mockArtifact(String name, String newVersion, String base, String repo, List<String> deps) throws Exception {
      // find current version and group name of artifact
      final Manifest manifest = Manifest.parseManifest(Paths.get(base).resolve("manifest.xml"));
      Artifact oldEntry = null;
      for (Artifact artifact : manifest.getArtifacts()) {
         if (artifact.getArtifactId().equals(name)) {
            oldEntry = artifact;
            break;
         }
      }

      if (oldEntry == null) {
         System.out.printf("Artifact %s not found", name);
         return;
      }
      // work out path in repo to current artifact
      final LocalRepository localRepository = new LocalRepository(Paths.get(repo));
      final File oldArtifact = localRepository.resolve(oldEntry);

//      // build a path to new version
//      final Manifest.Entry newEntry = new Manifest.Entry(oldEntry.aPackage, oldEntry.name, newVersion, oldEntry.classifier);
//      final Path newArtifact = repoPath.resolve(newEntry.getRelativePath());
//
//      // create new version folder
//      Files.createDirectories(newArtifact.getParent());
////
//      // copy artifact
//      Files.copy(oldArtifact, newArtifact);
//
//      // generate dependency manifest
      final DeployArtifact deployer = new DeployArtifact(repo);
      deployer.deploy(oldEntry.getGroupId(), oldEntry.getArtifactId(), newVersion, oldArtifact.toString());
      if (!deps.isEmpty()) {
         deployer.generateManifest(oldEntry.getGroupId(), oldEntry.getArtifactId(), newVersion, deps);
      }
      System.out.printf("Mocked %s %s as %s.%n", oldArtifact, oldEntry.getVersion(), newVersion);
   }

}
