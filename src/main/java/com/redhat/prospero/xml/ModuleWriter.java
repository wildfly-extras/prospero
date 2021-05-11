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
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.redhat.prospero.descriptors.Manifest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ModuleWriter {

   public static void updateVersionInModuleXml(Path module, Manifest.Entry oldVersion, Manifest.Entry newVersion) throws Exception {
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

   public static List<String> replaceArtifactWithResource(Path module) throws XmlException {
      try {
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
         factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
         factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
         Document input = factory.newDocumentBuilder().parse(module.toFile());

         XPath xpath = XPathFactory.newInstance().newXPath();
         String expr = "//resources/artifact";
         NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);

         final ArrayList<String> gavs = new ArrayList<>();
         for (int i = 0; i < nodes.getLength(); i++) {
            Element oldNode = (Element) nodes.item(i);
            final String[] gav = oldNode.getAttribute("name").split(":");

            final Element newNode = oldNode.getOwnerDocument().createElement("resource-root");
            if (gav.length == 3) {
               newNode.setAttribute("path", String.format("%s-%s.jar", gav[1], gav[2]));
            } else if (gav.length == 4) {
               newNode.setAttribute("path", String.format("%s-%s-%s.jar", gav[1], gav[2], gav[3]));
            } else {
               throw new Exception("Unrecognized gav " + String.join(":",gav));
            }
            // copy excludes
            final NodeList childNodes = oldNode.getChildNodes();
            for (int j=0; j < childNodes.getLength(); j++) {
               newNode.appendChild(childNodes.item(j).cloneNode(true));
            }
            oldNode.getParentNode().replaceChild(newNode, nodes.item(i));
         }

         TransformerFactory transformerFactory = TransformerFactory.newInstance();
         transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
         Transformer xformer = transformerFactory.newTransformer();
         Writer output = new FileWriter(module.toFile());
         xformer.transform(new DOMSource(input), new StreamResult(output));

         return gavs;
      } catch (Exception e) {
         throw new XmlException("" ,e);
      }
   }

}
