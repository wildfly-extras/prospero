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

package org.wildfly.prospero.api;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.eclipse.aether.artifact.Artifact;
import org.wildfly.channel.ChannelManifestCoordinate;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;

public class ArtifactUtils {
    public static int compareVersion(Artifact first, Artifact other) {
        return new ComparableVersion(first.getVersion()).compareTo(new ComparableVersion(other.getVersion()));
    }

    public static ChannelManifestCoordinate manifestFromString(String urlGavOrPath) {
        try {
            URL url = new URL(urlGavOrPath);
            return new ChannelManifestCoordinate(url);
        } catch (MalformedURLException e) {
            if (isValidCoordinate(urlGavOrPath)) {
                try {
                    return ChannelManifestCoordinate.create(null, urlGavOrPath);
                } catch (MalformedURLException ex) {
                    throw new RuntimeException(ex);
                }
            } else {
                // assume the string is a path
                try {
                    return new ChannelManifestCoordinate(Paths.get(urlGavOrPath).toAbsolutePath().toUri().toURL());
                } catch (MalformedURLException e2) {
                    throw new IllegalArgumentException("Can't convert path to URL", e2);
                }
            }
        }
    }

    public static boolean isValidCoordinate(String gav) {
        String[] parts = gav.split(":");
        return (parts.length == 3 // GAV
                && StringUtils.isNotBlank(parts[0])
                && StringUtils.isNotBlank(parts[1])
                && StringUtils.isNotBlank(parts[2]))
                ||
                (parts.length == 2 // GA
                        && StringUtils.isNotBlank(parts[0])
                        && StringUtils.isNotBlank(parts[1]));
    }
}
