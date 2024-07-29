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

package org.wildfly.prospero.cli;

import org.apache.commons.lang3.StringUtils;
import org.wildfly.channel.Repository;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RepositoryDefinition {

    private static final List<String> ALLOWED_SCHEMAS = Arrays.asList("file", "http", "https");

    public static List<Repository> from(List<String> repos) throws ArgumentParsingException {
        final ArrayList<Repository> repositories = new ArrayList<>(repos.size());
        for (int i = 0; i < repos.size(); i++) {
            final String repoInfo = repos.get(i);
            final String repoId;
            final String repoUri;

            if (repoInfo.contains("::")) {
                final String[] parts = repoInfo.split("::");
                if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                    throw CliMessages.MESSAGES.invalidRepositoryDefinition(repoInfo);
                }
                repoId = parts[0];
                repoUri = parseRepositoryLocation(parts[1], true);
            } else {
                if (StringUtils.isBlank(repoInfo)) {
                    throw CliMessages.MESSAGES.invalidRepositoryDefinition(repoInfo);
                }
                repoId = "temp-repo-" + i;
                repoUri = parseRepositoryLocation(repoInfo, true);
            }

            repositories.add(new Repository(repoId, repoUri));
        }
        return repositories;
    }

    static String parseRepositoryLocation(String location, boolean checkLocalPathExists) throws ArgumentParsingException {
        URI uri;
        try {
            uri = new URI(location);

            if ("file".equals(uri.getScheme()) || StringUtils.isBlank(uri.getScheme())) {
                if (StringUtils.isNotBlank(uri.getHost())) {
                    throw CliMessages.MESSAGES.unsupportedRemoteScheme(location);
                }

                // A "file:" URI with an empty host is assumed to be a local filesystem URL. An empty scheme would mean
                // a URI that is just a path without any "proto:" part, which is still assumed a local path.
                // A "file:" URI with a non-empty host would signify a remote URL, in which case we don't process it
                // further.
                if (!uri.isOpaque() && StringUtils.isNotBlank(uri.getScheme())) {
                    // The path starts with '/' character (not opaque) and has a scheme defined -> we can use
                    // `Path.of(uri)` to transform into a path, which gracefully handles Windows paths etc.
                    uri = normalizeLocalPath(Path.of(uri), checkLocalPathExists).toUri();
                } else {
                    // This is to handle relative URLs like "file:relative/path", which is outside of spec (URI is not
                    // hierarchical -> cannot use `Path.of(uri)`). Note that `uri.getSchemeSpecificPart()` rather than
                    // `uri.getPath()` because the URI class doesn't parse the path portion for opaque URIs.
                    uri = normalizeLocalPath(Path.of(uri.getSchemeSpecificPart()), checkLocalPathExists).toUri();
                }
            }

            // Resulting URI must be convertible to URL.
            //noinspection ResultOfMethodCallIgnored
            uri.toURL();

            // Check it is supported scheme.
            if (StringUtils.isNotBlank(uri.getScheme()) && !ALLOWED_SCHEMAS.contains(uri.getScheme())) {
                throw CliMessages.MESSAGES.unsupportedScheme(location);
            }
        } catch (URISyntaxException | MalformedURLException e) {
            try {
                // If the location is not a valid URI / URL, try to handle it as a path.
                Path path = Path.of(location);
                uri = normalizeLocalPath(path, checkLocalPathExists).toUri();
            } catch (InvalidPathException e2) {
                throw CliMessages.MESSAGES.invalidFilePath(location, e);
            }
        } catch (IllegalArgumentException e) {
            throw CliMessages.MESSAGES.invalidFilePath(location, e);
        }

        try {
            return uri.toURL().toExternalForm();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw CliMessages.MESSAGES.invalidFilePath(location, e);
        }
    }

    private static Path normalizeLocalPath(Path path, boolean checkPathExists) throws ArgumentParsingException {
        Path normalized = path.toAbsolutePath().normalize();
        if (checkPathExists && !Files.exists(path)) {
            throw CliMessages.MESSAGES.nonExistingFilePath(normalized);
        }
        return normalized;
    }

}
