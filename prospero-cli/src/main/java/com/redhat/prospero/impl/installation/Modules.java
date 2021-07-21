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

package com.redhat.prospero.impl.installation;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.redhat.prospero.api.Artifact;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class Modules {

   private final Path base;
   private HashMap<String, Set<Path>> moduleMapping = new HashMap<>();

   public Modules(Path base) {
      this.base = base;
   }

   public Collection<Path> find(Artifact artifact) {
      try {
         final Stream<Path> modules = Files.walk(base.resolve("modules"));
         if (moduleMapping.containsKey(artifact.getFileName())) {
            return moduleMapping.get(artifact.getFileName());
         }
         List<Path> updates = modules.filter(p-> p.getFileName().toString().equals("module.xml"))
            .filter(p->containsArtifact(p, artifact.getFileName())).collect(Collectors.toList());

         return updates;
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   private boolean containsArtifact(Path p, String artifactName) {
      try {
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
         factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
         factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
         Document input = factory.newDocumentBuilder().parse(p.toFile());

         XPath xpath = XPathFactory.newInstance().newXPath();
         String expr = "//resources/resource-root";
         NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);
         for (int i=0; i < nodes.getLength(); i++) {
            Element node = (Element)nodes.item(i);
            final String path = node.getAttribute("path");

            if (!path.endsWith(".jar")) {
               continue;
            }

            // build cache of modules
            if (!moduleMapping.containsKey(path)) {
               moduleMapping.put(path, new HashSet<>());
            }
            moduleMapping.get(path).add(p);

            if (path.equals(artifactName)) {
               return true;
            }
         }
         return false;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

}
