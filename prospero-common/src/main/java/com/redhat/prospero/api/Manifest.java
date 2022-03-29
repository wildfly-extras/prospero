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

package com.redhat.prospero.api;

import com.redhat.prospero.model.ManifestYamlSupport;
import org.eclipse.aether.artifact.Artifact;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.redhat.prospero.model.ManifestXmlSupport;
import com.redhat.prospero.model.XmlException;

public class Manifest {

    private final List<Artifact> artifacts;
    private final Path manifestFile;

    public Manifest(List<Artifact> artifacts, Path manifestFile) {
        this.artifacts = artifacts;
        this.manifestFile = manifestFile;
    }

    public static Manifest parseManifest(Path manifestPath) throws IOException {
        return ManifestYamlSupport.parse(manifestPath.toFile());
    }

    public List<Artifact> getArtifacts() {
        return new ArrayList<>(artifacts);
    }

    public Path getManifestFile() {
        return manifestFile;
    }

    public void updateVersion(Artifact newVersion) {
        // we can only update if we have old version of the same artifact
        Artifact oldArtifact = null;
        for (Artifact artifact : artifacts) {
            if (artifact.getGroupId().equals(newVersion.getGroupId()) && artifact.getArtifactId().equals(newVersion.getArtifactId())) {
                oldArtifact = artifact;
                break;
            }
        }

        if (oldArtifact == null) {
            throw new RuntimeException("Previous verison of " + newVersion.toString() + " not found.");
        }

        artifacts.remove(oldArtifact);
        artifacts.add(newVersion);
    }

    public Artifact find(Artifact gav) {
        for (Artifact artifact : artifacts) {
            if (artifact.getGroupId().equals(gav.getGroupId()) && artifact.getArtifactId().equals(gav.getArtifactId())) {
                return artifact;
            }
        }
        return null;
    }
}
