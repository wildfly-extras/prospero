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

package org.wildfly.prospero.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import org.wildfly.channel.maven.ChannelCoordinate;
import org.wildfly.prospero.Messages;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChannelRef {

    private final String url;

    private final String path;

    /**
     * The name of another previously named path, or of one of the standard paths provided by the system.
     * If 'relative-to' is provided, the value of the 'path' attribute is treated as relative to the path specified by this attribute.
     * This is an optional field.
     */
    private final String relativeTo;

    private final String gav;

    private final Type type;

    @JsonCreator
    public ChannelRef(@JsonProperty(required = true, value = "type") Type type, @JsonProperty(value = "gav") String gav, @JsonProperty(value = "url") String url, @JsonProperty(value = "path") String path, @JsonProperty(value = "relativeTo") String relativeTo) {
        this.type = type;
        this.gav = gav;
        this.url = url;
        this.path = path;
        this.relativeTo = relativeTo;
        validate();
    }

    private void validate() {
        switch (type) {
            case GAV:
                if (!isValidCoordinate(gav)) {
                    throw Messages.MESSAGES.invalidGAV(gav);
                }
                break;
            case URL:
                try {
                    URL url1 = new URL(url);
                } catch (MalformedURLException e) {
                    throw Messages.MESSAGES.invalidURL(url);
                }
                break;
            case PATH:
                // relativeTo must not be absolute.
                validateNonAbsolutePath(relativeTo);
                // find system property first, then env, otherwise throw exception.
                String relativeToStr = System.getProperty(relativeTo) != null ? System.getProperty(relativeTo) : System.getenv(relativeTo);
                if (relativeToStr == null) {
                    throw Messages.MESSAGES.invalidRelativeTo(relativeTo);
                }
                Path p = Paths.get(relativeToStr, path);
                if (!Files.exists(p)) {
                    throw Messages.MESSAGES.pathNotExist(p);
                }
                break;
            default:
                throw Messages.MESSAGES.invalidChannelType(type.toString());
        }
    }

    private static void validateNonAbsolutePath(String path) {
        if (isAbsoluteUnixOrWindowsPath(path)) {
            throw Messages.MESSAGES.invalidNonAbsolutePath(path);
        }
    }

    public ChannelRef(ChannelRef other) {
        this.type = other.getType();
        switch (other.getType()) {
            case GAV:
                this.gav = other.getGav();
                this.url = null;
                this.path = null;
                this.relativeTo = null;
                break;
            case URL:
                this.url = other.getUrl();
                this.gav = null;
                this.path = null;
                this.relativeTo = null;
                break;
            case PATH:
                this.path = other.getPath();
                this.relativeTo = other.getRelativeTo();
                this.gav = null;
                this.url = null;
                break;
            default:
                throw Messages.MESSAGES.invalidChannelType(other.getType().toString());
        }
    }

    public Type getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public String getPath() {
        return path;
    }

    public String getRelativeTo() {
        return relativeTo;
    }

    public String getGav() {
        return gav;
    }

    @JsonIgnore
    public String getGavOrUrlString() {
        if (Type.GAV.equals(this.type)) {
            return gav;
        } else if (Type.URL.equals(this.type)) {
            return url;
        } else {
            return this.path;
        }
    }

    @JsonIgnore
    public ChannelCoordinate toChannelCoordinate() {
        switch (this.type) {
            case GAV:
                final String[] splitGav = gav.split(":");
                return new ChannelCoordinate(splitGav[0], splitGav[1]);
            case URL:
                try {
                    return new ChannelCoordinate(new URL(url));
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            case PATH:
                try {
                    if (relativeTo == null) {
                        return new ChannelCoordinate(new URL(path));
                    } else {
                        // find system property first, then env, otherwise throw exception.
                        String relativeToStr = System.getProperty(relativeTo) != null ? System.getProperty(relativeTo) : System.getenv(relativeTo);
                        if (relativeToStr == null) {
                            throw Messages.MESSAGES.invalidRelativeTo(relativeTo);
                        }
                        URL urlx = Paths.get(relativeToStr, path).toUri().toURL();
                        return new ChannelCoordinate(urlx);
                    }
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            default:
                throw Messages.MESSAGES.invalidChannelType(this.getType().toString());
        }
    }

    @Override
    public String toString() {
        return "Channel{" + "gav='" + gav + '\'' + ", url='" + url + '\'' + ", path='" + path + '\'' + ", relative-to='" + relativeTo + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChannelRef that = (ChannelRef) o;
        return Objects.equals(url, that.url) && Objects.equals(path, that.path) && Objects.equals(relativeTo, that.relativeTo) && Objects.equals(gav, that.gav) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, path, relativeTo, gav, type);
    }

    public static ChannelRef fromString(String urlGavOrPath) {
        try {
            URL url = new URL(urlGavOrPath);
            return new ChannelRef(Type.URL, null, url.toExternalForm(), null, null);
        } catch (MalformedURLException e) {
            if (isValidCoordinate(urlGavOrPath)) {
                return new ChannelRef(Type.GAV, urlGavOrPath, null, null, null);
            } else {
                // assume the string is a path
                try {
                    return new ChannelRef(Type.URL, null,
                            Paths.get(urlGavOrPath).toAbsolutePath().toUri().toURL().toExternalForm(), null, null);
                } catch (MalformedURLException e2) {
                    throw new IllegalArgumentException("Can't convert path to URL", e2);
                }
            }
        }
    }

    public static ChannelRef fromString(String path, String relativeTo) {
        return new ChannelRef(Type.PATH, null, null, path, relativeTo);
    }

    public static Type determineChannelType(String channel) {
        try {
            URL url = new URL(channel);
            return Type.URL;
        } catch (MalformedURLException e) {
            if (isValidCoordinate(channel)) {
                return Type.GAV;
            } else {
                return Type.PATH;
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

    /**
     * This is copied from AbstractPathService.java of wildfly-core project.
     * Checks whether the given path looks like an absolute Unix or Windows filesystem pathname <strong>without
     * regard for what the filesystem is underlying the Java Virtual Machine</strong>. A UNIX pathname is
     * absolute if its prefix is <code>"/"</code>.  A Microsoft Windows pathname is absolute if its prefix is a drive
     * specifier followed by <code>"\\"</code>, or if its prefix is <code>"\\\\"</code>.
     * <p>
     * <strong>This method differs from simply creating a new {@code File} and calling {@link java.io.File#isAbsolute()} in that
     * its results do not change depending on what the filesystem underlying the Java Virtual Machine is. </strong>
     * </p>
     *
     * @param path the path
     *
     * @return  {@code true} if {@code path} looks like an absolute Unix or Windows pathname
     */
    public static boolean isAbsoluteUnixOrWindowsPath(final String path) {
        if (path != null) {
            int length = path.length();
            if (length > 0) {
                char c0 = path.charAt(0);
                if (c0 == '/') {
                    return true;   // Absolute Unix path
                } else if (length > 1) {
                    char c1 = path.charAt(1);
                    if (c0 == '\\' && c1 == '\\') {
                        return true;   // Absolute UNC pathname "\\\\foo"
                    } else return length > 2 && c1 == ':' && path.charAt(2) == '\\' && isDriveLetter(c0); // Absolute local pathname "z:\\foo"
                }

            }
        }
        return false;
    }

    private static boolean isDriveLetter(final char c) {
        return ((c >= 'a') && (c <= 'z')) || ((c >= 'A') && (c <= 'Z'));
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public enum Type {
        @JsonProperty("gav")
        GAV,
        @JsonProperty("url")
        URL,
        @JsonProperty("path")
        PATH
    }
}
