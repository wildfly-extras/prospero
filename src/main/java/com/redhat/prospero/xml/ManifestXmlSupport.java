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
import java.util.ArrayList;

import com.redhat.prospero.api.Artifact;
import com.redhat.prospero.api.Package;
import com.redhat.prospero.api.Manifest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ManifestXmlSupport extends XmlSupport {

   private static final ManifestXmlSupport INSTANCE = new ManifestXmlSupport();

   private ManifestXmlSupport() {

   }

   public static Manifest parse(File manifestFile) throws XmlException {
      return INSTANCE.doParse(manifestFile);
   }

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
         for (Package aPackage : manifest.getPackages()) {
            printWriter.println(String.format("<package group=\"%s\" name=\"%s\" version=\"%s\"/>",
                                              aPackage.getGroupId(), aPackage.getArtifactId(), aPackage.getVersion()));
         }

         // add artifacts
         for (Artifact artifact : manifest.getArtifacts()) {
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

   private Manifest doParse(File manifestFile) throws XmlException {
      Document input = readDocument(manifestFile);

      final ArrayList<Artifact> entries = parseArtifacts(input);
      final ArrayList<Package> packages = parsePackages(input);
      final Manifest manifest = new Manifest(entries, packages, manifestFile.toPath());
      return manifest;
   }

   private ArrayList<Artifact> parseArtifacts(Document input) throws XmlException {
      NodeList nodes = nodesFromXPath(input, "//artifact");
      final ArrayList<Artifact> entries = new ArrayList<>(nodes.getLength());
      for (int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         entries.add(new Artifact(node.getAttribute("package"),
                                  node.getAttribute("name"),
                                  node.getAttribute("version"),
                                  node.getAttribute("classifier")));
      }
      return entries;
   }

   private ArrayList<Package> parsePackages(Document input) throws XmlException {
      NodeList nodes = nodesFromXPath(input, "//package");
      final ArrayList<Package> entries = new ArrayList<>(nodes.getLength());
      for (int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         entries.add(new Package(node.getAttribute("group"),
                                 node.getAttribute("name"),
                                 node.getAttribute("version")));
      }
      return entries;
   }
}
