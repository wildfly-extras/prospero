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

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RepositoryDefinition {

    private static final Logger logger = Logger.getLogger(RepositoryDefinition.class.getName());

    public static List<Repository> from(List<String> repos) throws ArgumentParsingException {
        ArrayList<Repository> repositories = new ArrayList<>(repos.size());
        for (int i = 0; i < repos.size(); i++) {
            String repoInfo = repos.get(i);
            if(repoInfo.contains("::")) {
                final String[] parts = repoInfo.split("::");
                if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty() || !isValidUrl(parts[1])) {
                    throw CliMessages.MESSAGES.invalidRepositoryDefinition(repoInfo);
                }
                repositories.add(new Repository(parts[0], parts[1]));
            } else {
                if (!(repoInfo.startsWith("http://") || repoInfo.startsWith("https://"))  && !repoInfo.isEmpty()) {
                    try {
                        repoInfo = getAbsoluteFileURI(repoInfo).toString();
                    } catch (URISyntaxException e) {
                        logger.warn("An error occurred while processing URI");
                    }
                }
                if (!isValidUrl(repoInfo)){
                    throw CliMessages.MESSAGES.invalidRepositoryDefinition(repoInfo);
                }
                repositories.add(new Repository("temp-repo-" + i, repoInfo));
            }
        }
        return repositories;
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
        File file = new File(getPath(repoInfo));
        if (file.exists()) {
            return file.toURI();
        } else {
            throw CliMessages.MESSAGES.invalidFilePath(repoInfo);
        }
    }

    public static String getPath(String repoInfo) throws URISyntaxException {
        if (repoInfo.startsWith("file:")) {
            URI inputUri = new URI(repoInfo);
            if (inputUri.getScheme().equals("file") &&
                    !inputUri.getSchemeSpecificPart().startsWith("/")) {
                return Path.of(inputUri.getSchemeSpecificPart()).toAbsolutePath().normalize().toString();
            }
            return Path.of(inputUri).normalize().toFile().getAbsolutePath();
        } else {
            return Path.of(repoInfo).toAbsolutePath().normalize().toString();
        }
    }
}
