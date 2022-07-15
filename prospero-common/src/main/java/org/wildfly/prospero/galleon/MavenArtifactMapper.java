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

package org.wildfly.prospero.galleon;

import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.MavenArtifact;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MavenArtifactMapper {

    private final Collection<org.jboss.galleon.universe.maven.MavenArtifact> galleonArtifacts;
    private final HashMap<String, org.jboss.galleon.universe.maven.MavenArtifact> artifactMap = new HashMap<>();

    public MavenArtifactMapper(Collection<org.jboss.galleon.universe.maven.MavenArtifact> galleonArtifacts) {
        this.galleonArtifacts = galleonArtifacts;

        for (org.jboss.galleon.universe.maven.MavenArtifact a : galleonArtifacts) {
            artifactMap.put(coordString(a.getGroupId(), a.getArtifactId(), a.getExtension(), a.getClassifier()), a);
        }
    }

    private String coordString(String groupId, String artifactId, String extension, String classifier) {
        return String.format("%s:%s:%s:%s", groupId, artifactId, wrapNull(extension), wrapNull(classifier));
    }

    private String wrapNull(String value) {
        return value ==null?"": value;
    }

    public List<ArtifactCoordinate> toChannelArtifacts() {
        return galleonArtifacts.stream()
                .map(a -> new ArtifactCoordinate(a.getGroupId(), a.getArtifactId(), a.getExtension(), a.getClassifier(), a.getVersion()))
                .collect(Collectors.toList());
    }

    public static boolean isSameArtifact(MavenArtifact channelArtifact, org.jboss.galleon.universe.maven.MavenArtifact galleonArtifact) {
        return channelArtifact.getGroupId().equals(galleonArtifact.getGroupId()) &&
                channelArtifact.getArtifactId().equals(galleonArtifact.getArtifactId()) &&
                channelArtifact.getClassifier().equals(galleonArtifact.getClassifier()) &&
                channelArtifact.getExtension().equals(galleonArtifact.getExtension());
    }

    public Collection<org.jboss.galleon.universe.maven.MavenArtifact> applyResolution(List<MavenArtifact> channelArtifacts) {
        for (org.wildfly.channel.MavenArtifact channelArtifact : channelArtifacts) {
            String key = coordString(channelArtifact.getGroupId(), channelArtifact.getArtifactId(), channelArtifact.getExtension(), channelArtifact.getClassifier());
            if (!artifactMap.containsKey(key)) {
                throw new IllegalArgumentException("Unknown artifact: " + key);
            }
            resolve(artifactMap.get(key), channelArtifact);
        }
        return galleonArtifacts;
    }

    public static void resolve(org.jboss.galleon.universe.maven.MavenArtifact artifact, MavenArtifact resolvedArtifact) {
        Objects.requireNonNull(artifact);
        Objects.requireNonNull(resolvedArtifact);
        Objects.requireNonNull(resolvedArtifact.getFile());
        Objects.requireNonNull(resolvedArtifact.getVersion());

        artifact.setPath(resolvedArtifact.getFile().toPath());
        artifact.setVersion(resolvedArtifact.getVersion());
    }
}
