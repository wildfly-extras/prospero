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

package com.redhat.prospero.xml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import com.redhat.prospero.descriptors.Manifest;

public class ManifestWriter {
   public static void write(Manifest manifest, File manifestFile) throws XmlException {
      // if file exists backup it (move)
      if (manifestFile.exists()) {
         try {
            Files.move(manifestFile.toPath(), manifestFile.toPath().getParent().resolve(manifestFile.getName() + "_bkp"), StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException e) {
            throw new XmlException("Unable to backup manifest file", e);
         }
      }

      // export manifest
      try {
         final PrintWriter printWriter = new PrintWriter(manifestFile);
         printWriter.println("<manifest>");
         // add packages
         for (Manifest.Package aPackage : manifest.getPackages()) {
            printWriter.println(String.format("<package group=\"%s\" name=\"%s\" version=\"%s\"/>",
                                              aPackage.getGroupId(), aPackage.getArtifactId(), aPackage.getVersion()));
         }

         // add artifacts
         for (Manifest.Artifact artifact : manifest.getArtifacts()) {
            printWriter.println(String.format("<artifact package=\"%s\" name=\"%s\" version=\"%s\" classifier=\"%s\"/>",
                                              artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getClassifier()));
         }

         printWriter.println("</manifest>");
         printWriter.flush();
         printWriter.close();
      } catch (FileNotFoundException e) {
         throw new XmlException("Unable to write manifest", e);
      }

   }

   public static void write(Manifest manifest) throws XmlException {
      write(manifest, manifest.getManifestFile().toFile());
   }
}
