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

package org.wildfly.prospero.patch;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.wildfly.channel.ArtifactCoordinate;

public class Artifact {

    private String groupId;
    private String artifactId;
    private String version;
    private String classifier;
    private String extension;

    @JsonCreator
    public Artifact(@JsonProperty(value="groupId") String groupId,
                    @JsonProperty(value="artifactId") String artifactId,
                    @JsonProperty(value="classifier") String classifier,
                    @JsonProperty(value="extension") String extension,
                    @JsonProperty(value="version") String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.classifier = classifier;
        this.extension = extension==null?"jar":extension;
    }

    public static Artifact from(org.eclipse.aether.artifact.Artifact a) {
        return new Artifact(a.getGroupId(), a.getArtifactId(), a.getClassifier(), a.getExtension(), a.getVersion());
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

    public String getExtension() {
        return extension;
    }

    @JsonIgnore
    public ArtifactCoordinate toCoordinate() {
        return new ArtifactCoordinate(groupId, artifactId, extension, classifier, version);
    }
}
