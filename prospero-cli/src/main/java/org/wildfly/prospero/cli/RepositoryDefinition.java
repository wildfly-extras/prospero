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

import org.jboss.logging.Logger;
import org.wildfly.channel.Repository;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RepositoryDefinition {

    private static final Logger logger = Logger.getLogger(RepositoryDefinition.class.getName());

    public static List<Repository> from(List<String> repos) throws ArgumentParsingException {
        final ArrayList<Repository> repositories = new ArrayList<>(repos.size());
        for (int i = 0; i < repos.size(); i++) {
            final String repoInfo = repos.get(i);
            final String repoId;
            final String repoUri;

            try {
                if (repoInfo.contains("::")) {
                    final String[] parts = repoInfo.split("::");
                    if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                        throw CliMessages.MESSAGES.invalidRepositoryDefinition(repoInfo);
                    }
                    repoId = parts[0];
                    repoUri = parseRepositoryLocation(parts[1]);
                } else {
                    repoId = "temp-repo-" + i;
                    repoUri = parseRepositoryLocation(repoInfo);
                }
                repositories.add(new Repository(repoId, repoUri));
            } catch (URISyntaxException e) {
                logger.error("Unable to parse repository uri + " + repoInfo, e);
                throw CliMessages.MESSAGES.invalidRepositoryDefinition(repoInfo);
            }

        }
        return repositories;
    }

    private static String parseRepositoryLocation(String repoLocation) throws URISyntaxException, ArgumentParsingException {
        if (!isRemoteUrl(repoLocation) && !repoLocation.isEmpty()) {
            // the repoLocation contains either a file URI or a path
            // we need to convert it to a valid file IR
            repoLocation = getAbsoluteFileURI(repoLocation).toString();
        }
        if (!isValidUrl(repoLocation)){
            throw CliMessages.MESSAGES.invalidRepositoryDefinition(repoLocation);
        }
        return repoLocation;
    }

    private static boolean isRemoteUrl(String repoInfo) {
        return repoInfo.startsWith("http://") || repoInfo.startsWith("https://");
    }

    private static boolean isValidUrl(String text) {
        try {
            new URL(text);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public static URI getAbsoluteFileURI(String repoInfo) throws ArgumentParsingException, URISyntaxException {
        final Path repoPath = getPath(repoInfo).toAbsolutePath().normalize();
        if (Files.exists(repoPath)) {
            return repoPath.toUri();
        } else {
            throw CliMessages.MESSAGES.invalidFilePath(repoInfo);
        }
    }

    public static Path getPath(String repoInfo) throws URISyntaxException, ArgumentParsingException {
        if (repoInfo.startsWith("file:")) {
            final URI inputUri = new URI(repoInfo);
            if (containsAbsolutePath(inputUri)) {
                return Path.of(inputUri);
            } else {
                return Path.of(inputUri.getSchemeSpecificPart());
            }
        } else {
            try {
                return Path.of(repoInfo);
            } catch (InvalidPathException e) {
                throw CliMessages.MESSAGES.invalidFilePath(repoInfo);
            }
        }
    }

    private static boolean containsAbsolutePath(URI inputUri) {
        // absolute paths in URI (even on Windows) has to start with slash. If not we treat it as a relative path
        return inputUri.getSchemeSpecificPart().startsWith("/");
    }
}
