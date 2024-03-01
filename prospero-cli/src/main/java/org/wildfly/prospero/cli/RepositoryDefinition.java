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

import org.wildfly.channel.Repository;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class RepositoryDefinition {

    public static List<Repository> from(List<String> repos) throws ArgumentParsingException {
        ArrayList<Repository> repositories = new ArrayList<>(repos.size());
        for (int i = 0; i < repos.size(); i++) {
            final String text = repos.get(i);
            if(text.contains("::")) {
                final String[] parts = text.split("::");
                if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty() || !isValidUrl(parts[1])) {
                    throw CliMessages.MESSAGES.invalidRepositoryDefinition(text);
                }

                repositories.add(new Repository(parts[0], parts[1]));
            } else {
                if (!isValidUrl(text)) {
                    throw CliMessages.MESSAGES.invalidRepositoryDefinition(text);
                }

                repositories.add(new Repository("temp-repo-" + i, text));
            }
        }
        return repositories;
    }

    private static boolean isValidUrl(String text) {
        try {
            URL url = new URL(text);
            if (text.startsWith("file")){
                String path = Paths.get(url.getPath()).normalize().toString();
                File f = new File(path);
                return f.exists();
            }
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }
}
