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
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.lingala.zip4j.ZipFile;
import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class TestEnvBuilder {

   public static final Path LOCAL_MVN_REPO = Paths.get(System.getProperty("user.home"), ".m2", "repository");

   public static void main(String[] args) throws Exception {
      if (args.length < 3) {
         System.out.println("Not enough parameters. Need to provide source (thin) WFLY installation folder, and paths where target and repo folders will be created.");
         return;
      }
      final String base = args[0];
      final String targetWfly = args[1];
      final String targetRepo = args[2];

      copyStructure(Paths.get(base), Paths.get(targetWfly));
      exportManifest(Paths.get(targetWfly, "modules"), Paths.get(targetWfly, "manifest.xml"));
      replaceArtifactsWithResources(Paths.get(targetWfly, "modules"));
      exportMvnRepo(Paths.get(targetWfly, "manifest.xml"), LOCAL_MVN_REPO, Paths.get(targetRepo));
      generatePackages(Paths.get(targetWfly), Paths.get(targetRepo));

      FileUtils.deleteDirectory(Paths.get(targetWfly).toFile());
   }

   private static void generatePackages(Path base, Path repo) throws Exception {
      System.out.println("Creating WFLY packages");

      final ZipFile baseZip = new ZipFile("wildfly-base-1.0.0.zip");
      baseZip.addFiles(Arrays.asList("LICENSE.txt", "README.txt", "copyright.txt", "jboss-modules.jar", "manifest.xml")
                          .stream().map(p->base.resolve(p).toFile()).collect(Collectors.toList()));
      baseZip.addFolder(base.resolve("welcome-content").toFile());
      exportToRepo(repo, baseZip.getFile(), "wildfly-base", "1.0.0");

      final ZipFile binZip = new ZipFile("wildfly-bin-1.0.0.zip");
      binZip.addFolder(base.resolve("bin").toFile());
      exportToRepo(repo, binZip.getFile(), "wildfly-bin", "1.0.0");

      final ZipFile modulesZip = new ZipFile("wildfly-modules-1.0.0.zip");
      modulesZip.addFolder(base.resolve("modules").toFile());
      exportToRepo(repo, modulesZip.getFile(), "wildfly-modules", "1.0.0");

      final ZipFile standaloneZip = new ZipFile("wildfly-standalone-1.0.0.zip");
      standaloneZip.addFolder(base.resolve("standalone").toFile());
      exportToRepo(repo, standaloneZip.getFile(), "wildfly-standalone", "1.0.0");
   }

   private static void exportToRepo(Path repo, File zip, String name, String version) throws IOException {
      final Path targetDir = repo.resolve(Paths.get("org", "wildfly", "prospero", name, version));
      Files.createDirectories(targetDir);
      Files.move(zip.toPath(), targetDir.resolve(zip.getName()));
   }

   private static void exportMvnRepo(Path manifestPath, Path sourceRepo, Path targetRepo) throws XPathExpressionException, ParserConfigurationException, IOException, SAXException {
      System.out.println("Building test repository at " + targetRepo + ".");
      final Manifest manifest = Manifest.parseManifest(manifestPath);

      manifest.getEntries().stream().forEach(entry-> {
         // build relative path
         final Path relativePath = entry.getRelativePath();

         // verify source exists
         if (!sourceRepo.resolve(relativePath).toFile().exists()) {
            throw new RuntimeException("Source file not found: " + entry.getFileName());
         }

         // copy to target
         try {
            FileUtils.copyFile(sourceRepo.resolve(relativePath).toFile(), targetRepo.resolve(relativePath).toFile());
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      });
   }

   private static void copyStructure(Path source, Path target) throws Exception {
      System.out.println("Coping thin WFLY installation to " + target + ".");
      FileUtils.copyDirectory(source.toFile(), target.toFile());
   }

   private static void exportManifest(Path modulesPath, Path manifestPath) throws Exception {
      System.out.println("Creating manifest of module libraries.");
      final Stream<Path> modules = Files.walk(modulesPath);

      File f = manifestPath.toFile();
      final PrintWriter printWriter = new PrintWriter(f);
      printWriter.println("<manifest>");
      // add packages
      for (String pack : Arrays.asList("wildfly-bin", "wildfly-modules", "wildfly-standalone")) {
         printWriter.println(String.format("<package group=\"%s\" name=\"%s\" version=\"%s\"/>",
                                        "org.wildfly.prospero", pack, "1.0.0"));

      }

      // add artifacts
      modules.filter(p-> p.getFileName().toString().equals("module.xml"))
         .flatMap(p-> TestEnvBuilder.extractArtifacts(p).stream())
         .forEach(gav -> {
            final String[] coords = gav.split(":");
            if (coords.length != 3 && coords.length != 4) {
               System.out.println("!!!! Unexpected GAV: " + gav);
            }
            printWriter.println(String.format("<artifact package=\"%s\" name=\"%s\" version=\"%s\" classifier=\"%s\"/>",
                                              coords[0], coords[1], coords[2], coords.length==4?coords[3]:""));
         });

      printWriter.println("</manifest>");
      printWriter.flush();
      printWriter.close();
   }

   private static void replaceArtifactsWithResources(Path modulesPath) throws Exception {
      System.out.println("Changing module descriptors to use local resources instead of Maven GAVs.");
      final Stream<Path> modules = Files.walk(modulesPath);
      modules.filter(p-> p.getFileName().toString().equals("module.xml"))
         .forEach(p-> replaceArtifact(p));

   }

   private static List<String> replaceArtifact(Path module) {
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
         e.printStackTrace();
         return null;
      }
   }

   private static List<String> extractArtifacts(Path module) {
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
         e.printStackTrace();
         return null;
      }
   }

}
