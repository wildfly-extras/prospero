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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.redhat.prospero.api.Artifact;
import com.redhat.prospero.api.Gav;
import com.redhat.prospero.api.Package;
import com.redhat.prospero.api.Manifest;
import com.redhat.prospero.xml.ManifestXmlSupport;
import com.redhat.prospero.xml.ModuleXmlSupport;
import com.redhat.prospero.xml.XmlException;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;

public class DemoInitializer {

   public static final Path LOCAL_MVN_REPO = Paths.get(System.getProperty("user.home"), ".m2", "repository");

   public static void main(String[] args) throws Exception {
      if (args.length < 3) {
         System.out.println("Not enough parameters. Need to provide source (thin) WFLY installation folder, and paths where target and repo folders will be created.");
         return;
      }
      final String base = args[0];
      final String targetWfly = args[1];
      final String targetRepo = args[2];

      copyStructure(Paths.get(base), Paths.get(targetWfly));
      exportManifest(Paths.get(targetWfly, "modules"), Paths.get(targetWfly, "manifest.xml"));
      replaceArtifactsWithResources(Paths.get(targetWfly, "modules"));
      exportMvnRepo(Paths.get(targetWfly, "manifest.xml"), LOCAL_MVN_REPO, Paths.get(targetRepo));
      generatePackages(Paths.get(targetWfly), Paths.get(targetRepo));

      FileUtils.deleteDirectory(Paths.get(targetWfly).toFile());
   }

   private static void generatePackages(Path base, Path repo) throws Exception {
      System.out.println("Creating WFLY packages");

      final ZipFile baseZip = new ZipFile("wildfly-base-1.0.0.zip");
      baseZip.addFiles(Arrays.asList("LICENSE.txt", "README.txt", "copyright.txt", "jboss-modules.jar", "manifest.xml")
                          .stream().map(p->base.resolve(p).toFile()).collect(Collectors.toList()));
      baseZip.addFolder(base.resolve("welcome-content").toFile());
      exportToRepo(repo, baseZip.getFile(), "wildfly-base", "1.0.0");

      final ZipFile binZip = new ZipFile("wildfly-bin-1.0.0.zip");
      binZip.addFolder(base.resolve("bin").toFile());
      exportToRepo(repo, binZip.getFile(), "wildfly-bin", "1.0.0");

      final ZipFile modulesZip = new ZipFile("wildfly-modules-1.0.0.zip");
      modulesZip.addFolder(base.resolve("modules").toFile());
      exportToRepo(repo, modulesZip.getFile(), "wildfly-modules", "1.0.0");

      final ZipFile standaloneZip = new ZipFile("wildfly-standalone-1.0.0.zip");
      standaloneZip.addFolder(base.resolve("standalone").toFile());
      exportToRepo(repo, standaloneZip.getFile(), "wildfly-standalone", "1.0.0");
   }

   private static void exportToRepo(Path repo, File zip, String name, String version) throws IOException {
      final Path targetDir = repo.resolve(Paths.get("org", "wildfly", "prospero", name, version));
      Files.createDirectories(targetDir);
      Files.move(zip.toPath(), targetDir.resolve(zip.getName()));
   }

   private static void exportMvnRepo(Path manifestPath, Path sourceRepo, Path targetRepo) throws XmlException {
      System.out.println("Building test repository at " + targetRepo + ".");
      final Manifest manifest = Manifest.parseManifest(manifestPath);

      manifest.getArtifacts().stream().forEach(entry-> {
         // build relative path
         final Path relativePath = getRelativePath(entry);

         // verify source exists
         if (!sourceRepo.resolve(relativePath).toFile().exists()) {
            throw new RuntimeException("Source file not found: " + entry.getFileName());
         }

         // copy to target
         try {
            FileUtils.copyFile(sourceRepo.resolve(relativePath).toFile(), targetRepo.resolve(relativePath).toFile());
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      });
   }

   private static Path getRelativePath(Gav artifact) {
      List<String> path = new ArrayList<>();
      String start = null;
      for (String f : artifact.getGroupId().split("\\.")) {
         if (start == null) {
            start = f;
         } else {
            path.add(f);
         }
      }
      path.add(artifact.getArtifactId());
      path.add(artifact.getVersion());
      path.add(artifact.getFileName());

      return Paths.get(start, path.toArray(new String[]{}));
   }

   private static void copyStructure(Path source, Path target) throws Exception {
      System.out.println("Coping thin WFLY installation to " + target + ".");
      FileUtils.copyDirectory(source.toFile(), target.toFile());
   }

   private static void exportManifest(Path modulesPath, Path manifestPath) throws Exception {
      System.out.println("Creating manifest of module libraries.");

      // add packages
      List<Package> packages = Stream.of("wildfly-bin", "wildfly-modules", "wildfly-standalone")
         .map(pack -> new Package("org.wildfly.prospero", pack, "1.0.0"))
         .collect(Collectors.toList());

      // add artifacts
      final Stream<Path> modules = Files.walk(modulesPath);
      List<Artifact> artifacts = modules.filter(p-> p.getFileName().toString().equals("module.xml"))
         .flatMap(p-> {
            try {
               return ModuleXmlSupport.INSTANCE.extractArtifacts(p).stream();
            } catch (XmlException e) {
               throw new RuntimeException(e);
            }
         })
         .map(gav -> {
            final String[] coords = gav.split(":");
            if (coords.length != 3 && coords.length != 4) {
               throw new RuntimeException("Unexpected GAV: " + gav);
            }
            return new Artifact(coords[0], coords[1], coords[2], coords.length==4?coords[3]:"");
         }).collect(Collectors.toList());

      Manifest manifest = new Manifest(artifacts, packages, manifestPath);
      ManifestXmlSupport.write(manifest, manifestPath.toFile());
   }

   private static void replaceArtifactsWithResources(Path modulesPath) throws Exception {
      System.out.println("Changing module descriptors to use local resources instead of Maven GAVs.");
      final Stream<Path> modules = Files.walk(modulesPath);

      modules.filter(p-> p.getFileName().toString().equals("module.xml"))
         .forEach(p-> {
            try {
               ModuleXmlSupport.INSTANCE.replaceArtifactWithResource(p);
            } catch (XmlException e) {
               throw new RuntimeException(e);
            }
         });

   }
}
