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
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

class Manifest {

   private final ArrayList<Entry> entries;
   private final Path manifestFile;
   private final ArrayList<Package> packages;

   private Manifest(ArrayList<Entry> entries, ArrayList<Package> packages, Path manifestFile) {
      this.entries = entries;
      this.packages = packages;
      this.manifestFile = manifestFile;
   }

   static Manifest parseManifest(Path manifestPath) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document input = factory.newDocumentBuilder().parse(manifestPath.toFile());

      final ArrayList<Entry> entries = parseArtifacts(input);
      final ArrayList<Package> packages = parsePackages(input);
      final Manifest manifest = new Manifest(entries, packages, manifestPath);
      return manifest;
   }

   private static ArrayList<Entry> parseArtifacts(Document input) throws XPathExpressionException {
      XPath xpath = XPathFactory.newInstance().newXPath();
      String expr = "//artifact";
      NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);
      final ArrayList<Entry> entries = new ArrayList<>(nodes.getLength());
      for (int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         entries.add(new Entry(node.getAttribute("package"),
                            node.getAttribute("name"),
                            node.getAttribute("version"),
                            node.getAttribute("classifier")));
      }
      return entries;
   }

   private static ArrayList<Package> parsePackages(Document input) throws XPathExpressionException {
      XPath xpath = XPathFactory.newInstance().newXPath();
      String expr = "//package";
      NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);
      final ArrayList<Package> entries = new ArrayList<>(nodes.getLength());
      for (int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         entries.add(new Package(node.getAttribute("group"),
                               node.getAttribute("name"),
                               node.getAttribute("version")));
      }
      return entries;
   }

   public ArrayList<Entry> getEntries() {
      return new ArrayList<>(entries);
   }

   public ArrayList<Package> getPackages() {
      return packages;
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

      // export
      try {
         exportChange(oldEntry, newVersion);
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   private void exportChange(Entry oldEntry, Entry newEntry) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document input = factory.newDocumentBuilder().parse(manifestFile.toFile());

      XPath xpath = XPathFactory.newInstance().newXPath();
      String expr = String.format("//artifact[contains(@name, '%s') and contains(@package, '%s') and contains(@classifier, '%s')]",
                                  oldEntry.name, oldEntry.aPackage, oldEntry.classifier);
      NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);
      for (int i = 0; i < nodes.getLength(); i++) {
         Element node = (Element) nodes.item(i);
         node.setAttribute("version", newEntry.version);
      }

      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Transformer xformer = transformerFactory.newTransformer();
      Writer output = new FileWriter(manifestFile.toFile());
      xformer.transform(new DOMSource(input), new StreamResult(output));
   }

   static class Entry {

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

   static class Package {

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
