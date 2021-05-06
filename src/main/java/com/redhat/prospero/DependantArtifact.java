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

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class Dependency {

   public final String group;
   public final String name;
   public final String version;
   public final String classifier;

   private Dependency(String group, String name, String version, String classifier) {
      this.group = group;
      this.name = name;
      this.version = version;
      this.classifier = classifier;
   }

   public static Dependency parseXml(Document input) throws Exception {
      String group = readNode(input, "//group");
      String name = readNode(input, "//name");
      String version = readNode(input, "//version");
      String classifier = readNode(input, "//classifier");

      return new Dependency(group, name, version, classifier);
   }

   private static String readNode(Document input, String expr) throws Exception {
      XPath xpath = XPathFactory.newInstance().newXPath();
      NodeList nodes = (NodeList) xpath.evaluate(expr, input, XPathConstants.NODESET);

      if (nodes.getLength() != 1) {
         throw new Exception(String.format("Parse error: should only have one %s node", expr));
      }

      String group = nodes.item(0).getNodeValue();
      return group;
   }

   public class Dep {
      
   }
}
