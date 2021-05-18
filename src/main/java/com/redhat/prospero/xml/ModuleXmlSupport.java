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
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.redhat.prospero.api.Artifact;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ModuleXmlSupport extends XmlSupport {

   public static final ModuleXmlSupport INSTANCE = new ModuleXmlSupport();

   public List<String> extractArtifacts(Path module) throws XmlException {
      try {
         Document input = readDocument(module.toFile());

         NodeList nodes = nodesFromXPath(input, "//resources/artifact");

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

   public void updateVersionInModuleXml(Path module, Artifact oldVersion, Artifact newVersion) throws XmlException {
      Document input = readDocument(module.toFile());

      String expr = String.format("//resources/resource-root[contains(@path, '%s')]", oldVersion.getFileName());
      NodeList nodes = nodesFromXPath(input, expr);

      for (int i = 0; i < nodes.getLength(); i++) {
         Element oldNode = (Element) nodes.item(i);

         oldNode.setAttribute("path", newVersion.getFileName());
      }

      transform(module, input);
   }

   public List<String> replaceArtifactWithResource(Path module) throws XmlException {
      Document input = readDocument(module.toFile());

      NodeList nodes = nodesFromXPath(input, "//resources/artifact");

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
            throw new XmlException("Unrecognized gav " + String.join(":",gav));
         }
         // copy excludes
         final NodeList childNodes = oldNode.getChildNodes();
         for (int j=0; j < childNodes.getLength(); j++) {
            newNode.appendChild(childNodes.item(j).cloneNode(true));
         }
         oldNode.getParentNode().replaceChild(newNode, nodes.item(i));
      }

      transform(module, input);

      return gavs;
   }

   private void transform(Path module, Document input) throws XmlException {
      try {
         TransformerFactory transformerFactory = TransformerFactory.newInstance();
         transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
         Transformer xformer = transformerFactory.newTransformer();
         Writer output = new FileWriter(module.toFile());
         xformer.transform(new DOMSource(input), new StreamResult(output));
      } catch (TransformerException | IOException e) {
         throw new XmlException("Error writting updated module.xml", e);
      }
   }


}
