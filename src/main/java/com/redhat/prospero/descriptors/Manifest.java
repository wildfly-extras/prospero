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

package com.redhat.prospero.descriptors;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.redhat.prospero.xml.ManifestReader;
import com.redhat.prospero.xml.XmlException;

public class Manifest {

   private final List<Entry> entries;
   private final Path manifestFile;
   private final List<Package> packages;

   public Manifest(List<Entry> entries, List<Package> packages, Path manifestFile) {
      this.entries = entries;
      this.packages = packages;
      this.manifestFile = manifestFile;
   }

   public static Manifest parseManifest(Path manifestPath) throws XmlException {
      return ManifestReader.parse(manifestPath.toFile());
   }

   public List<Entry> getEntries() {
      return new ArrayList<>(entries);
   }

   public List<Package> getPackages() {
      return packages;
   }

   public Path getManifestFile() {
      return manifestFile;
   }

   public void updateVersion(Entry newVersion) {
      // we can only update if we have old version of the same artifact
      Entry oldEntry = null;
      for (Entry entry : entries) {
         if (entry.aPackage.equals(newVersion.aPackage) && entry.name.equals(newVersion.name) && entry.classifier.equals(newVersion.classifier)) {
            oldEntry = entry;
            break;
         }
      }

      if (oldEntry == null) {
         throw new RuntimeException("Previous verison of " + newVersion.getFileName() + " not found.");
      }

      entries.remove(oldEntry);
      entries.add(newVersion);
   }

   public Entry find(String group, String name, String classifier) {
      for (Entry entry : entries) {
         if (entry.aPackage.equals(group) && entry.name.equals(name) && entry.classifier.equals(classifier)) {
            return entry;
         }
      }
      return null;
   }

   public static class Entry {

      public final String aPackage;
      public final String name;
      public final String version;
      public final String classifier;

      public Entry(String aPackage, String name, String version, String classifier) {
         this.aPackage = aPackage;
         this.name = name;
         this.version = version;
         this.classifier = classifier;
      }

      public String getFileName() {
         if (classifier == null || classifier.length() == 0) {
            return String.format("%s-%s.jar", name, version);
         } else {
            return String.format("%s-%s-%s.jar", name, version, classifier);
         }
      }

      public Path getRelativePath() {
         List<String> path = new ArrayList<>();
         String start = null;
         for (String f : aPackage.split("\\.")) {
            if (start == null) {
               start = f;
            } else {
               path.add(f);
            }
         }
         path.add(name);
         path.add(version);
         path.add(getFileName());

         return Paths.get(start, path.toArray(new String[]{}));
      }

      public Entry newVersion(String newVersion) {
         return new Entry(aPackage, name, newVersion, classifier);
      }
   }

   public static class Package {

      public final String group;
      public final String name;
      public final String version;

      public Package(String group, String name, String version) {
         this.group = group;
         this.name = name;
         this.version = version;
      }

      public String getFileName() {
         return String.format("%s-%s.zip", name, version);
      }

      public Path getRelativePath() {
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
         path.add(getFileName());

         return Paths.get(start, path.toArray(new String[]{}));
      }
   }
}
