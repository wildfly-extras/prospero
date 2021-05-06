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

package com.redhat.prospero;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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
            System.out.printf("Updating [%s:%s]\t\t%s => %s%n", entry.aPackage, entry.name, entry.version, latestVersion.version);

            // find module defining old version
            Collection<Path> updates = modules.find(entry);

            if (updates.isEmpty()) {
               throw new RuntimeException("Artifact " + entry.getFileName() + " not found");
            }

            for (Path module : updates) {
               // copy the new artifact
               Path target = module.getParent();
               try {
                  FileUtils.copyFile(repo.resolve(latestVersion.getRelativePath()).toFile(), target.resolve(latestVersion.getFileName()).toFile());

                  // update model.xml
                  updateVersionInModuleXml(module, entry, latestVersion);

                  // update manifest.xml
                  manifest.updateVersion(latestVersion);
               } catch (Exception e) {
                  throw new RuntimeException(e);
               }
            }
            System.out.printf("  Done [%s:%s]", entry.aPackage, entry.name);
         }
      }

      if (updatesFound) {
         System.out.println();
         System.out.println("Updates completed.");
      }else {
         System.out.println("Installation up-to-date, no updates found.");
      }
   }

   private void updateVersionInModuleXml(Path module, Manifest.Entry oldVersion, Manifest.Entry newVersion) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document input = factory.newDocumentBuilder().parse(module.toFile());

      XPath xpath = XPathFactory.newInstance().newXPath();
      String expr = String.format("//resources/resource-root[contains(@path, '%s')]", oldVersion.getFileName());
      NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);

      final ArrayList<String> gavs = new ArrayList<>();
      for (int i = 0; i < nodes.getLength(); i++) {
         Element oldNode = (Element) nodes.item(i);

         oldNode.setAttribute("path", newVersion.getFileName());
      }

      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer xformer = transformerFactory.newTransformer();
      Writer output = new FileWriter(module.toFile());
      xformer.transform(new DOMSource(input), new StreamResult(output));
   }
}
