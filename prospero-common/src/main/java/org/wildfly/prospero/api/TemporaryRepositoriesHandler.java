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

import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.Messages;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TemporaryRepositoriesHandler {

    public static List<Channel> addRepositories(List<Channel> originalChannels, List<Repository> additionalRepositories) {
        Objects.requireNonNull(originalChannels);
        Objects.requireNonNull(additionalRepositories);

        if (additionalRepositories.isEmpty()) {
            return new ArrayList<>(originalChannels);
        }

        ArrayList<Channel> mergedChannels = new ArrayList<>(originalChannels.size());

        for (Channel oc : originalChannels) {
            final List<Repository> repositories = mergeRepositories(oc.getRepositories(), additionalRepositories);
            final Channel c = new Channel(oc.getSchemaVersion(), oc.getName(), oc.getDescription(), oc.getVendor(),
                    oc.getChannelRequirements(), repositories, oc.getManifestRef());
            mergedChannels.add(c);
        }

        return mergedChannels;
    }

    public static List<Repository> from(List<String> repos) {
        ArrayList<Repository> repositories = new ArrayList<>(repos.size());
        for (int i = 0; i < repos.size(); i++) {
            final String text = repos.get(i);
            if(text.contains("::")) {
                final String[] parts = text.split("::");
                if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty() || !isValidUrl(parts[1])) {
                    throw Messages.MESSAGES.invalidRepositoryDefinition(text);
                }

                repositories.add(new Repository(parts[0], parts[1]));
            } else {
                if (!isValidUrl(text)) {
                    throw Messages.MESSAGES.invalidRepositoryDefinition(text);
                }

                repositories.add(new Repository("temp-repo-" + i, text));
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

    private static List<Repository> mergeRepositories(List<Repository> originalRepositories, List<Repository> additionalRepositories) {
        return additionalRepositories;
    }
}
