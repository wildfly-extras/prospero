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
import org.jboss.galleon.universe.maven.MavenArtifact;

import java.util.Objects;

public class Artifact {

    protected final String groupId;
    protected final String artifactId;
    protected final String version;
    protected final String classifier;
    protected final String packaging;

    public Artifact(String groupId, String artifactId, String version, String classifier) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.packaging = "jar";
    }

    public Artifact(String groupId, String artifactId, String version, String classifier, String packaging) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.packaging = packaging;
    }

    public static Artifact from(MavenArtifact mavenArtifact) {
        return new Artifact(mavenArtifact.getGroupId(), mavenArtifact.getArtifactId(), mavenArtifact.getVersion(),
                mavenArtifact.getClassifier(), mavenArtifact.getExtension());
    }

    public Artifact newVersion(String newVersion) {
        return new Artifact(groupId, artifactId, newVersion, classifier, packaging);
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getClassifier() {
        return classifier;
    }

    public String getPackaging() {
        return packaging;
    }

    public String getFileName() {
        if (classifier == null || classifier.length() == 0) {
            return String.format("%s-%s.%s", artifactId, version, packaging);
        } else {
            return String.format("%s-%s-%s.%s", artifactId, version, classifier, packaging);
        }
    }

    public int compareVersion(Artifact other) {
        return new ComparableVersion(this.getVersion()).compareTo(new ComparableVersion(other.getVersion()));
    }

    @Override
    public String toString() {
        return "Gav{" + "groupId='" + groupId + '\'' + ", artifactId='" + artifactId + '\'' + ", version='" + version + '\'' + ", classifier='" + classifier + '\'' + ", packaging='" + packaging + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Artifact gav = (Artifact) o;
        return groupId.equals(gav.groupId) && artifactId.equals(gav.artifactId) && version.equals(gav.version) && classifier.equals(gav.classifier) && packaging.equals(gav.packaging);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, classifier, packaging);
    }
}
