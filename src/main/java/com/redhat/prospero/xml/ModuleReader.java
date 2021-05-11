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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ModuleReader {

   public static List<String> extractArtifacts(Path module) throws XmlException {
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
            Element value = (Element) nodes.item(i);
            final String gav = value.getAttribute("name");
            gavs.add(gav);
         }
         return gavs;
      } catch (Exception e) {
         throw new XmlException("Error reading artifacts in module XML", e);
      }
   }

}
