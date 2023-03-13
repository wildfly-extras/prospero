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
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.prospero.Messages;
import org.wildfly.channel.ChannelMetadataCoordinate;
import org.wildfly.channel.maven.ChannelCoordinate;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;

public class ArtifactUtils {
    public static int compareVersion(Artifact first, Artifact other) {
        return new ComparableVersion(first.getVersion()).compareTo(new ComparableVersion(other.getVersion()));
    }

    public static ChannelManifestCoordinate manifestCoordFromString(String urlGavOrPath) {
        return coordinateFromString(urlGavOrPath, ChannelManifestCoordinate.class);
    }

    public static ChannelCoordinate channelCoordFromString(String urlGavOrPath) {
        return coordinateFromString(urlGavOrPath, ChannelCoordinate.class);
    }

    private static <T extends ChannelMetadataCoordinate> T coordinateFromString(String urlGavOrPath, Class<T> clazz) {
        try {
            try {
                URL url = new URL(urlGavOrPath);
                return clazz.getDeclaredConstructor(URL.class).newInstance(url);
            } catch (MalformedURLException e) {
                if (isValidCoordinate(urlGavOrPath)) {
                    String[] gav = urlGavOrPath.split(":");
                    if (gav.length == 2) {
                        return clazz.getDeclaredConstructor(String.class, String.class).newInstance(gav[0], gav[1]);
                    } else if (gav.length == 3) {
                        return clazz.getDeclaredConstructor(String.class, String.class, String.class)
                                .newInstance(gav[0], gav[1], gav[2]);
                    }
                }
                // assume the string is a path
                try {
                    return clazz.getDeclaredConstructor(URL.class)
                            .newInstance(Paths.get(urlGavOrPath).toAbsolutePath().toUri().toURL());
                } catch (MalformedURLException e2) {
                    throw Messages.MESSAGES.invalidUrl(urlGavOrPath, e2);
                }
            }
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Can't instantiate " + clazz.getSimpleName(), e);
        }
    }

    public static boolean isValidCoordinate(String gav) {
        if (gav.contains("\\") || gav.contains("/")) { // must not contain slash or backslash -> could be a path
            return false;
        }

        String[] parts = gav.split(":");
        for (String part: parts) { // no segment can be empty or null
            if (StringUtils.isBlank(part)) {
                return false;
            }
        }
        if (parts.length != 2 && parts.length != 3) { // GA or GAV
            return false;
        }

        return true;
    }

    public static String printStream(ArtifactCoordinate coord) {
        return String.format("%s:%s", coord.getGroupId(), coord.getArtifactId());
    }

    public static String printCoordinate(ArtifactCoordinate coord) {
        return printCoordinate(coord.getGroupId(), coord.getArtifactId(), coord.getClassifier(),
                coord.getExtension(), coord.getVersion());
    }

    public static String printCoordinate(ChannelMetadataCoordinate coord) {
        if (coord.getUrl() != null) {
            return coord.getUrl().toString();
        }

        return printCoordinate(coord.getGroupId(), coord.getArtifactId(), coord.getClassifier(),
                coord.getExtension(), coord.getVersion());
    }

    private static String printCoordinate(String groupId, String artifactId, String coordinate, String extension, String version) {
        StringBuilder sb = new StringBuilder();
        sb.append(groupId).append(":").append(artifactId);
        if (coordinate !=null && !coordinate.isEmpty()) {
            sb.append(":").append(coordinate);
        }
        if (extension !=null && !extension.isEmpty()) {
            sb.append(":").append(extension);
        }
        if (version !=null && !version.isEmpty()) {
            sb.append(":").append(version);
        }
        return sb.toString();
    }
}
