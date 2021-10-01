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

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.universe.maven.MavenArtifact;

public class ArtifactUtils {
    public static int compareVersion(Artifact first, Artifact other) {
        return new ComparableVersion(first.getVersion()).compareTo(new ComparableVersion(other.getVersion()));
    }

    public static String getFileName(Artifact artifact) {
        if (artifact.getClassifier() == null || artifact.getClassifier().length() == 0) {
            return String.format("%s-%s.%s", artifact.getArtifactId(), artifact.getVersion(), artifact.getExtension());
        } else {
            return String.format("%s-%s-%s.%s", artifact.getArtifactId(), artifact.getVersion(), artifact.getClassifier(), artifact.getExtension());
        }
    }

    public static Artifact from(MavenArtifact artifact) {
        return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getExtension(), artifact.getVersion());
    }
}
