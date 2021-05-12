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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.redhat.prospero.api.Artifact;
import com.redhat.prospero.api.Package;
import com.redhat.prospero.api.Manifest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ManifestReader {

   public static Manifest parse(File manifestFile) throws XmlException {
      try {
         return parseManifest(manifestFile);
      } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException e) {
         throw new XmlException("Error while reading manifest file", e);
      }
   }

   private static Manifest parseManifest(File manifestFile) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document input = factory.newDocumentBuilder().parse(manifestFile);

      final ArrayList<Artifact> entries = parseArtifacts(input);
      final ArrayList<Package> packages = parsePackages(input);
      final Manifest manifest = new Manifest(entries, packages, manifestFile.toPath());
      return manifest;
   }

   private static ArrayList<Artifact> parseArtifacts(Document input) throws XPathExpressionException {
      XPath xpath = XPathFactory.newInstance().newXPath();
      String expr = "//artifact";
      NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);
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
}
