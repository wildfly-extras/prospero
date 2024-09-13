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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TemporaryRepositoriesHandler {

    /**
     * Prepares the channels in {@code originalChannels} to use temporary repositories in {@code repositories}.
     *
     * Resulting repositories have the following changes:
     * <ul>
     *     <li>previously configured repositories replaced with {@code repositories}</li>
     *     <li>disabled GPG checks</li>
     * </ul>
     *
     * @param originalChannels
     * @param repositories
     * @return
     */
    public static List<Channel> overrideRepositories(List<Channel> originalChannels, List<Repository> repositories) {
        Objects.requireNonNull(originalChannels);
        Objects.requireNonNull(repositories);

        if (repositories.isEmpty()) {
            return new ArrayList<>(originalChannels);
        }

        ArrayList<Channel> mergedChannels = new ArrayList<>(originalChannels.size());

        for (Channel oc : originalChannels) {
            final Channel c = new Channel.Builder(oc)
                    .setRepositories(repositories)
                    .setGpgCheck(false)
                    .build();
            mergedChannels.add(c);
        }

        return mergedChannels;
    }

}
