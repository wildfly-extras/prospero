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

package com.redhat.prospero.actions;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.TreeSet;

import com.redhat.prospero.descriptors.DependencyDescriptor;
import com.redhat.prospero.descriptors.Manifest;
import com.redhat.prospero.modules.Modules;
import com.redhat.prospero.xml.ManifestWriter;
import com.redhat.prospero.xml.ModuleWriter;
import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;

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
      
      new Update().update(Paths.get(base), Paths.get(repo), artifact);
   }

   private void update(Path base, Path repo, String artifact) throws Exception {
      final Manifest manifest = Manifest.parseManifest(base.resolve("manifest.xml"));
      final Modules modules = new Modules(base);

      boolean updatesFound = false;
      for (Manifest.Entry entry : manifest.getEntries()) {
         if (artifact != null && !artifact.equals(entry.name)) {
            continue;
         }
         final Path relativePath = entry.getRelativePath();
         // check if newer version exists
         final String[] versions = repo.resolve(relativePath).getParent().getParent().toFile().list();
         Manifest.Entry latestVersion = null;
         if (versions.length > 1) {
            final TreeSet<ComparableVersion> comparableVersions = new TreeSet<>();
            for (String version : versions) {
               comparableVersions.add(new ComparableVersion(version));
            }

            String latestVersionSting = comparableVersions.descendingSet().first().toString();
            if (!latestVersionSting.equals(entry.version)) {
               latestVersion = entry.newVersion(latestVersionSting);
            }
         }

         if (latestVersion != null) {
            updatesFound = true;

            // check if dependency info exists
            final Path dependenciesXml = repo.resolve(latestVersion.getRelativePath().getParent()).resolve("dependencies.xml");
            if (dependenciesXml.toFile().exists()) {
               final DependencyDescriptor dependencyDescriptor = DependencyDescriptor.parseXml(dependenciesXml);

               // check if all dependencies are at least on the min version
               for (DependencyDescriptor.Dependency dep : dependencyDescriptor.deps) {
                  Manifest.Entry e = manifest.find(dep.group, dep.name, dep.classifier);

                  if (new ComparableVersion(e.version).compareTo(new ComparableVersion(dep.minVersion)) < 0) {
                     System.out.println("Found upgrades required for " + latestVersion.name);
                     // update dependencies if needed
                     update(base, repo, e.name);
                  }
                  // verify new version is enough
               }

            }

            System.out.printf("Updating [%s:%s]\t\t%s => %s%n", entry.aPackage, entry.name, entry.version, latestVersion.version);

            // find module defining old version
            Collection<Path> updates = modules.find(entry);

            if (updates.isEmpty()) {
               throw new RuntimeException("Artifact " + entry.getFileName() + " not found");
            }

            try {
               for (Path module : updates) {
                  // copy the new artifact
                  Path target = module.getParent();
                  FileUtils.copyFile(repo.resolve(latestVersion.getRelativePath()).toFile(), target.resolve(latestVersion.getFileName()).toFile());

                  // update model.xml
                  ModuleWriter.updateVersionInModuleXml(module, entry, latestVersion);

                  // update manifest.xml
                  manifest.updateVersion(latestVersion);
               }

               ManifestWriter.write(manifest);
            } catch (Exception e) {
               throw new RuntimeException(e);
            }

            System.out.printf("  Done [%s:%s]%n", entry.aPackage, entry.name);
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
