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

package com.redhat.prospero.impl;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Gav;
import com.redhat.prospero.descriptors.DependencyDescriptor;
import com.redhat.prospero.descriptors.Manifest;
import com.redhat.prospero.xml.DependencyDescriptorParser;
import com.redhat.prospero.xml.XmlException;
import org.apache.maven.artifact.versioning.ComparableVersion;

public class LocalRepository {

   private final Path base;

   public LocalRepository(Path base) {
      this.base = base;
   }

   public File resolve(Gav artifact) throws ArtifactNotFoundException {
      final File file = base.resolve(getRelativePath(artifact)).toFile();
      if (!file.exists()) {
         throw new ArtifactNotFoundException("Unable to resolve artifact " + artifact);
      }
      return file;
   }

   public Gav findLatestVersionOf(Gav artifact) {
      final String[] versions = base.resolve(getRelativePath(artifact)).getParent().getParent().toFile().list();

      Gav latestVersion = null;
      if (versions.length > 1) {
         final TreeSet<ComparableVersion> comparableVersions = new TreeSet<>();
         for (String version : versions) {
            comparableVersions.add(new ComparableVersion(version));
         }

         String latestVersionSting = comparableVersions.descendingSet().first().toString();
         if (!latestVersionSting.equals(artifact.getVersion())) {
            latestVersion = artifact.newVersion(latestVersionSting);
         }
      }
      return latestVersion;
   }

   public DependencyDescriptor resolveDescriptor(Gav latestVersion) throws XmlException {
      final Path descriptorPath = base.resolve(getRelativePath(latestVersion).getParent()).resolve("dependencies.xml");
      if (descriptorPath.toFile().exists()) {
         return DependencyDescriptorParser.parse(descriptorPath.toFile());
      } else {
         return null;
      }
   }

   private Path getRelativePath(Gav artifact) {
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
}
