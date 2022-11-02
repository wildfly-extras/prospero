/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.galleon;

import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.UniverseSpec;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.wildfly.prospero.Messages;

public class FeaturePackLocationParser {

    public static FeaturePackLocation resolveFpl(String fplText) {
        final FeaturePackLocation fpl = FeaturePackLocation.fromString(fplText);

        // full GAV
        if (fpl.isMavenCoordinates() || fpl.getUniverse() != null) {
            return fpl;
        }

        final String[] parts = fplText.split(":");
        if (parts.length == 1) {
            throw new IllegalArgumentException(Messages.MESSAGES.invalidFpl(fplText));
        }

        MavenArtifact artifact = new MavenArtifact();
        artifact.setGroupId(parts[0]);
        artifact.setArtifactId(parts[1]);
        artifact.setVersion(null);
        artifact.setExtension("zip");

        return new FeaturePackLocation(UniverseSpec.fromString("maven"), fpl.getProducerName() + ":" + fpl.getChannelName() + "::zip",
                null, null, artifact.getVersion());
    }
}
