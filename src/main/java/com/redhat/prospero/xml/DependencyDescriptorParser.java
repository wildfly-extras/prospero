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

import com.redhat.prospero.descriptors.DependencyDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class DependencyDescriptorParser {

   public static DependencyDescriptor parse(File descriptorFile) throws XmlException {
      try {
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
         factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
         factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
         Document input = factory.newDocumentBuilder().parse(descriptorFile);

         String group = readNode(input, "/artifact/group");
         String name = readNode(input, "/artifact/name");
         String version = readNode(input, "/artifact/version");
         String classifier = readNode(input, "/artifact/classifier");

         XPath xpath = XPathFactory.newInstance().newXPath();
         NodeList nodes = (NodeList) xpath.evaluate("//dependencies/dependency", input, XPathConstants.NODESET);
         ArrayList<DependencyDescriptor.Dependency> deps = new ArrayList<>(nodes.getLength());
         for (int i = 0; i < nodes.getLength(); i++) {
            deps.add(parseDependency(nodes.item(i)));
         }

         return new DependencyDescriptor(group, name, version, classifier, deps);
      } catch (ParserConfigurationException | XPathExpressionException | SAXException | IOException e) {
         throw new XmlException("Failed to parse dependency descriptor", e);
      }
   }

   private static String readNode(Document input, String expr) throws XmlException, XPathExpressionException {
      XPath xpath = XPathFactory.newInstance().newXPath();
      NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);

      if (nodes.getLength() != 1) {
         throw new XmlException(String.format("Parse error: should only have one %s node", expr));
      }

      return nodes.item(0).getTextContent();
   }

   private static DependencyDescriptor.Dependency parseDependency(Node dep) throws XmlException {
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
               throw new XmlException("Unexpected element in dependency descriptor: " + node.getNodeName());
         }
      }

      return new DependencyDescriptor.Dependency(group, name, minVersion, classifier);
   }
}
