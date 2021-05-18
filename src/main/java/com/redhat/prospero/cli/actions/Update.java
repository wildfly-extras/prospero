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

import java.nio.file.Paths;

import com.redhat.prospero.api.Artifact;
import com.redhat.prospero.api.Gav;
import com.redhat.prospero.api.ArtifactDependencies;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.impl.installation.LocalInstallation;
import com.redhat.prospero.impl.repository.LocalRepository;
import com.redhat.prospero.xml.ManifestXmlSupport;

public class Update {

   public static void main(String[] args) throws Exception {
      if (args.length < 2) {
         System.out.println("Not enough parameters. Need to provide WFLY installation and repository folders.");
         return;
      }
      final String base = args[0];
      final String repo = args[1];
      final String artifact;
      if (args.length == 3) {
         // TODO: take multiple artifacts
         artifact = args[2];
      } else {
         artifact = null;
      }

      LocalRepository localRepository = new LocalRepository(Paths.get(repo));
      LocalInstallation localInstallation = new LocalInstallation(Paths.get(base));
      new Update().update(localRepository, localInstallation, artifact);
   }

   public void update(LocalRepository localRepository, LocalInstallation localInstallation, String artifactId) throws Exception {
      final Manifest manifest = localInstallation.getManifest();

      boolean updatesFound = false;
      for (Artifact artifact : manifest.getArtifacts()) {
         // TODO: extract to Gav/Artifact
         if (artifactId != null && !artifactId.equals(artifact.getArtifactId())) {
            continue;
         }

         final Gav latestVersion = localRepository.findLatestVersionOf(artifact);

         if (latestVersion != null) {
            updatesFound = true;

            // check if dependency info exists
            final ArtifactDependencies artifactDependencies = localRepository.resolveDescriptor(latestVersion);
            if (artifactDependencies != null) {

               // check if all dependencies are at least on the min version
               for (Artifact dep : artifactDependencies.getDependencies()) {
                  Artifact current = manifest.find(dep);

                  if (current.compareVersion(dep) < 0) {
                     System.out.println("Found upgrades required for " + latestVersion.getArtifactId());
                     // update dependencies if needed
                     update(localRepository, localInstallation, current.getArtifactId());
                  }
                  // TODO: verify new version is enough
               }

            }

            System.out.printf("Updating [%s:%s]\t\t%s => %s%n", artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), latestVersion.getVersion());
            // TODO: handle zip upgrade as well
            localInstallation.updateArtifact(artifact, (Artifact) latestVersion, localRepository.resolve(latestVersion));

            ManifestXmlSupport.write(manifest);
            System.out.printf("  Done [%s:%s]%n", artifact.getGroupId(), artifact.getArtifactId());
         }
      }

      if (updatesFound) {
         System.out.println();
         System.out.println("Updates completed.");
      }else {
         System.out.println("Installation up-to-date, no updates found.");
      }
   }
}
