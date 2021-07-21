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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class XmlSupport {

   protected XPath xpath = XPathFactory.newInstance().newXPath();

   protected Document readDocument(File xmlFile) throws XmlException {
      try {
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
         factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
         factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
         Document input = factory.newDocumentBuilder().parse(xmlFile);
         return input;
      } catch (IOException | ParserConfigurationException | SAXException e) {
         throw new XmlException("Failed to parse XML descriptor", e);
      }
   }

   protected NodeList nodesFromXPath(Node input, String expr) throws XmlException {
      try {
         NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);
         return nodes;
      } catch (XPathExpressionException e) {
         throw new XmlException("Failed to parse XML descriptor", e);
      }
   }
}
