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

package com.redhat.prospero.impl.repository;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import com.redhat.prospero.api.ArtifactNotFoundException;
import com.redhat.prospero.api.Gav;
import com.redhat.prospero.api.ArtifactDependencies;
import com.redhat.prospero.api.Repository;
import com.redhat.prospero.xml.ArtifactDependencyReader;
import com.redhat.prospero.xml.XmlException;
import org.apache.maven.artifact.versioning.ComparableVersion;

public class LocalRepository implements Repository {

   private final Path base;

   public LocalRepository(Path base) {
      this.base = base;
   }

   @Override
   public File resolve(Gav artifact) throws ArtifactNotFoundException {
      final File file = base.resolve(getRelativePath(artifact)).toFile();
      if (!file.exists()) {
         throw new ArtifactNotFoundException("Unable to resolve artifact " + artifact);
      }
      return file;
   }

   @Override
   public Gav findLatestVersionOf(Gav artifact) {
      final String[] versions = listVersions(artifact);

      Gav latestVersion = null;
      if (versions == null || versions.length == 0) {
         return artifact;
      }
      if (versions.length > 1) {
         final TreeSet<ComparableVersion> comparableVersions = new TreeSet<>();
         for (String version : versions) {
            comparableVersions.add(new ComparableVersion(version));
         }

         String latestVersionSting = comparableVersions.descendingSet().first().toString();
         return artifact.newVersion(latestVersionSting);
      } else {
         return artifact.newVersion(versions[0]);
      }
   }

   @Override
   public ArtifactDependencies resolveDescriptor(Gav latestVersion) throws XmlException {
      final Path descriptorPath = base.resolve(getRelativePath(latestVersion).getParent()).resolve("dependencies.xml");
      if (descriptorPath.toFile().exists()) {
         return ArtifactDependencyReader.parse(descriptorPath.toFile());
      } else {
         final String[] versions = listVersions(latestVersion);
         if (versions.length == 1) {
            return null;
         }

         final TreeSet<ComparableVersion> comparableVersions = new TreeSet<>();
         for (String version : versions) {
            comparableVersions.add(new ComparableVersion(version));
         }

         for (ComparableVersion comparableVersion : comparableVersions.descendingSet()) {
            final Path path = base.resolve(getRelativePath(latestVersion.newVersion(comparableVersion.toString())).getParent()).resolve("dependencies.xml");
            if (path.toFile().exists()) {
               return ArtifactDependencyReader.parse(path.toFile());
            }
         }

         return null;
      }
   }

   private String[] listVersions(Gav artifact) {
      final String[] versions = base.resolve(getRelativePath(artifact)).getParent().getParent().toFile().list();
      return versions;
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
