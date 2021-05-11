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

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import java.nio.file.Path;
import java.util.ArrayList;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DependencyDescriptor {

   public final String group;
   public final String name;
   public final String version;
   public final String classifier;
   public final ArrayList<Dependency> deps;

   private DependencyDescriptor(String group, String name, String version, String classifier, ArrayList<Dependency> deps) {
      this.group = group;
      this.name = name;
      this.version = version;
      this.classifier = classifier;
      this.deps = deps;
   }

   public static DependencyDescriptor parseXml(Path path) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document input = factory.newDocumentBuilder().parse(path.toFile());

      String group = readNode(input, "/artifact/group");
      String name = readNode(input, "/artifact/name");
      String version = readNode(input, "/artifact/version");
      String classifier = readNode(input, "/artifact/classifier");

      XPath xpath = XPathFactory.newInstance().newXPath();
      NodeList nodes = (NodeList) xpath.evaluate("//dependencies/dependency", input, XPathConstants.NODESET);
      ArrayList<Dependency> deps = new ArrayList<>(nodes.getLength());
      for (int i = 0; i < nodes.getLength(); i++) {
         deps.add(parseDependency(nodes.item(i)));
      }

      return new DependencyDescriptor(group, name, version, classifier, deps);
   }

   private static Dependency parseDependency(Node dep) throws Exception {
      String group = null;
      String name = null;
      String minVersion = null;
      String classifier = null;
      for (int i = 0; i < dep.getChildNodes().getLength(); i++) {
         final Node node = dep.getChildNodes().item(i);
         switch (node.getNodeName()) {
            case "group":
               group = node.getTextContent();
               break;
            case "name":
               name = node.getTextContent();
               break;
            case "minVersion":
               minVersion = node.getTextContent();
               break;
            case "classifier":
               classifier = node.getTextContent();
               break;
            case  "#text":
               // ignore
               break;
            default:
               throw new Exception("Unexpected element: " + node.getNodeName());
         }
      }

      return new Dependency(group, name, minVersion, classifier);

   }

   private static String readNode(Document input, String expr) throws Exception {
      XPath xpath = XPathFactory.newInstance().newXPath();
      NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);

      if (nodes.getLength() != 1) {
         throw new Exception(String.format("Parse error: should only have one %s node", expr));
      }

      return nodes.item(0).getTextContent();
   }

   public static class Dependency {
      public final String group;
      public final String name;
      public final String minVersion;
      public final String classifier;

      public Dependency(String group, String name, String minVersion, String classifier) {
         this.group = group;
         this.name = name;
         this.minVersion = minVersion;
         this.classifier = classifier;
      }
   }
}
