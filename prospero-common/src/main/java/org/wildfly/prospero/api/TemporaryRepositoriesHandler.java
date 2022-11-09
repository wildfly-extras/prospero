/*
 *
 *  * Copyright 2022 Red Hat, Inc. and/or its affiliates
 *  * and other contributors as indicated by the @author tags.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.wildfly.prospero.api;

import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        return IntStream.range(0, repos.size())
                .mapToObj(i -> new Repository("temp-repo-" + i, repos.get(i)))
                .collect(Collectors.toList());
    }

    private static List<Repository> mergeRepositories(List<Repository> originalRepositories, List<Repository> additionalRepositories) {
        return additionalRepositories;
    }
}
