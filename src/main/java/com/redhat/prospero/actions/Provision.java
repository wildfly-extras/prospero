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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import com.redhat.prospero.descriptors.Manifest;
import com.redhat.prospero.impl.LocalInstallation;
import com.redhat.prospero.impl.LocalRepository;
import com.redhat.prospero.modules.Modules;
import net.lingala.zip4j.ZipFile;
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
      System.out.println("Installing base package");

      // install base package
      String basePackageGroup = "org.wildfly.prospero";
      String basePackageArtifact = "wildfly-base";
      String basePackageVersion = "1.0.0";

      final LocalRepository localRepository = new LocalRepository(repo);
      final Manifest.Package basePackage = new Manifest.Package(basePackageGroup, basePackageArtifact, basePackageVersion);
      File basePackageFile = localRepository.resolve(basePackage);

      final LocalInstallation localInstallation = LocalInstallation.provision(base, basePackageFile);


      localInstallation.installPackage(basePackageFile);

      // go through manifest
      final Manifest manifest = Manifest.parseManifest(base.resolve("manifest.xml"));

      System.out.println("Installing remaining packages");
      // resolve packages
      for (Manifest.Package aPackage : manifest.getPackages()) {
         System.out.printf("Installing package [%s] %n", aPackage.getArtifactId());
         File packageFile = localRepository.resolve(aPackage);
         localInstallation.installPackage(packageFile);
      }

      System.out.println("Resolving artifacts from " + repo);
//      final Modules modules = new Modules(base);
      // foreach artifact
      for (Manifest.Artifact entry : manifest.getArtifacts()) {//  download from maven repo
         // TODO: use proper maven resolution :)
         File artifactFile = localRepository.resolve(entry);
         localInstallation.installArtifact(entry, artifactFile);
      }
      System.out.println("Installation provisioned and ready at " + base);
   }

   private void unzip(File archive, Path target) throws Exception {
      new ZipFile(archive).extractAll(target.toString());
   }
}
