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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class Modules {

   private final Path base;

   public Modules(Path base) {
      this.base = base;
   }

   public List<Path> find(Manifest.Entry entry) {
      try {
         final Stream<Path> modules = Files.walk(base.resolve("modules"));
         List<Path> updates = modules.filter(p-> p.getFileName().toString().equals("module.xml"))
            .filter(p->containsArtifact(p, entry.getFileName())).collect(Collectors.toList());

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
         String expr = String.format("//resources/resource-root[contains(@%s, '%s')]", "path", artifactName);
         NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);
         return nodes.getLength() > 0;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

}
