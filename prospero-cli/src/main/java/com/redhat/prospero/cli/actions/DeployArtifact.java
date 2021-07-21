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

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DeployArtifact {

   private final String repo;

   public DeployArtifact(String repo) {
      this.repo = repo;
   }

   public static void main(String[] args) throws Exception {
      String group = args[0];
      String artifact = args[1];
      String version = args[2];
      String fileToDeploy = args[3];
      String repo = args[4];
      List<String> deps = new ArrayList<>();

      if (args.length > 5) {
         for (int i=5; i< args.length; i++) {
            deps.add(args[i]);
         }
      }

      final DeployArtifact installer = new DeployArtifact(repo);
      installer.deploy(group, artifact, version, fileToDeploy);

      if (!deps.isEmpty()) {
         installer.generateManifest(group, artifact, version, deps);
      }
   }

   public void generateManifest(String group, String artifact, String version, List<String> deps) throws Exception {
      final Path manifestPath = Paths.get(repo).resolve(getRelativePath(group, artifact, version).getParent().resolve("dependencies.xml"));
      final PrintWriter printWriter = new PrintWriter(manifestPath.toFile());
      printWriter.println("<artifact>");
      printWriter.printf("<group>%s</group>%n", group);
      printWriter.printf("<name>%s</name>%n", artifact);
      printWriter.printf("<version>%s</version>%n", version);
      printWriter.printf("<classifier></classifier>%n");

      printWriter.println("<dependencies>");
      for (String dep : deps) {
         final String[] gav = dep.split(":");
         printWriter.println("<dependency>");
         printWriter.printf("<group>%s</group>%n", gav[0]);
         printWriter.printf("<name>%s</name>%n", gav[1]);
         printWriter.printf("<minVersion>%s</minVersion>%n", gav[2]);
         printWriter.printf("<classifier></classifier>%n");
         printWriter.println("</dependency>");
      }
      printWriter.println("</dependencies>");

      printWriter.println("</artifact>");
      printWriter.flush();
      printWriter.close();
   }

   public void deploy(String group, String artifact, String version, String fileToDeploy) throws Exception {
      final Path relativePath = getRelativePath(group, artifact, version);
      final Path repoPath = Paths.get(repo);

      // If file already exists exit
      final Path pathInRepo = repoPath.resolve(relativePath);
      if (pathInRepo.toFile().exists()) {
         System.out.printf("Artifact %s already deployed %n", relativePath);
         return;
      }

      // create directory structure
      Files.createDirectories(pathInRepo.getParent());

      // copy artifact
      Files.copy(Paths.get(fileToDeploy), pathInRepo);
   }

   public Path getRelativePath(String group, String name, String version) {
      List<String> path = new ArrayList<>();
      String start = null;
      for (String f : group.split("\\.")) {
         if (start == null) {
            start = f;
         } else {
            path.add(f);
         }
      }
      path.add(name);
      path.add(version);
      path.add(String.format("%s-%s.jar", name, version));

      return Paths.get(start, path.toArray(new String[]{}));
   }

}
