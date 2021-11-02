/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.prospero.model;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.redhat.prospero.api.Manifest;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ManifestXmlSupport extends XmlSupport {

    private static final ManifestXmlSupport INSTANCE = new ManifestXmlSupport();

    private ManifestXmlSupport() {

    }

    public static Manifest parse(File manifestFile) throws XmlException {
        return INSTANCE.doParse(manifestFile);
    }

    public static void write(Manifest manifest, File manifestFile) throws XmlException {
        // if file exists backup it (move)
        if (manifestFile.exists()) {
            try {
                Files.move(manifestFile.toPath(), manifestFile.toPath().getParent().resolve(manifestFile.getName() + "_bkp"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new XmlException("Unable to backup manifest file", e);
            }
        }

        // export manifest
        try {
            final PrintWriter printWriter = new PrintWriter(manifestFile);
            printWriter.println("<manifest>");

            // add artifacts
            for (Artifact artifact : sortArtifacts(manifest)) {
                printWriter.println(String.format("<artifact package=\"%s\" name=\"%s\" version=\"%s\" classifier=\"%s\" extension=\"%s\"/>",
                        artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getClassifier(), artifact.getExtension()));
            }

            printWriter.println("</manifest>");
            printWriter.flush();
            printWriter.close();
        } catch (FileNotFoundException e) {
            throw new XmlException("Unable to write manifest", e);
        }

    }

    private static List<Artifact> sortArtifacts(Manifest manifest) {
        List<Artifact> sorted = new ArrayList<>(manifest.getArtifacts());
        Collections.sort(sorted, (a1, a2)-> {
            if (!a1.getGroupId().equals(a2.getGroupId())) {
                return a1.getGroupId().compareTo(a2.getGroupId());
            }
            if (!a1.getArtifactId().equals(a2.getArtifactId())) {
                return a1.getArtifactId().compareTo(a2.getArtifactId());
            }
            if (!a1.getClassifier().equals(a2.getClassifier())) {
                return a1.getClassifier().compareTo(a2.getClassifier());
            }
            if (!a1.getExtension().equals(a2.getExtension())) {
                return a1.getExtension().compareTo(a2.getExtension());
            }
            return a1.getVersion().compareTo(a2.getVersion());
        });
        return sorted;
    }

    public static void write(Manifest manifest) throws XmlException {
        write(manifest, manifest.getManifestFile().toFile());
    }

    private Manifest doParse(File manifestFile) throws XmlException {
        Document input = readDocument(manifestFile);

        final ArrayList<Artifact> entries = parseArtifacts(input);
        final Manifest manifest = new Manifest(entries, manifestFile.toPath());
        return manifest;
    }

    private ArrayList<Artifact> parseArtifacts(Document input) throws XmlException {
        NodeList nodes = nodesFromXPath(input, "//artifact");
        final ArrayList<Artifact> entries = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            entries.add(new DefaultArtifact(node.getAttribute("package"),
                    node.getAttribute("name"),
                    node.getAttribute("classifier"),
                    node.getAttribute("extension"),
                    node.getAttribute("version")
                    ));
        }
        return entries;
    }
}
