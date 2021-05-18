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
import java.util.ArrayList;

import com.redhat.prospero.api.Artifact;
import com.redhat.prospero.api.ArtifactDependencies;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ArtifactDependencyReader extends XmlSupport {

   private static final ArtifactDependencyReader INSTANCE = new ArtifactDependencyReader();

   public static ArtifactDependencies parse(File descriptorFile) throws XmlException {
      return INSTANCE.doParse(descriptorFile);
   }

   private ArtifactDependencies doParse(File descriptorFile) throws XmlException {
      Document input = readDocument(descriptorFile);

      String group = readTextValue(input, "/artifact/group");
      String name = readTextValue(input, "/artifact/name");
      String version = readTextValue(input, "/artifact/version");
      String classifier = readTextValue(input, "/artifact/classifier");

      NodeList nodes = nodesFromXPath(input, "//dependencies/dependency");
      ArrayList<Artifact> deps = new ArrayList<>(nodes.getLength());
      for (int i = 0; i < nodes.getLength(); i++) {
         deps.add(parseDependency(nodes.item(i)));
      }

      return new ArtifactDependencies(new Artifact(group, name, version, classifier), deps);
   }

   private String readTextValue(Node input, String expr) throws XmlException {
      NodeList nodes = nodesFromXPath(input, expr);

      if (nodes.getLength() != 1) {
         throw new XmlException(String.format("Parse error: should only have one %s node", expr));
      }

      return nodes.item(0).getTextContent();
   }

   private Artifact parseDependency(Node dep) throws XmlException {
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

      return new Artifact(group, name, minVersion, classifier);
   }
}
