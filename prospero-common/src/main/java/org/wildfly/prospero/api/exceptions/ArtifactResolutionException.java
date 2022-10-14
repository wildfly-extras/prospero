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

package org.wildfly.prospero.api.exceptions;

import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.UnresolvedMavenArtifactException;
import org.wildfly.prospero.api.RepositoryUtils;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class ArtifactResolutionException extends OperationException {

    private static final Set<String> OFFLINE_REPOSITORIES = Set.of(MavenSessionManager.AETHER_OFFLINE_PROTOCOLS_VALUE.split(","));

    private Collection<RemoteRepository> repositories;
    private boolean offline;

    public ArtifactResolutionException(String msg, Throwable e) {
        super(msg, e);
    }

    public ArtifactResolutionException(String msg) {
        super(msg);
    }

    public ArtifactResolutionException(Throwable e) {
        super(e);
    }

    public ArtifactResolutionException(UnresolvedMavenArtifactException e, Collection<RemoteRepository> repositories, boolean offline) {
        super(e.getLocalizedMessage(), e);
        this.repositories = repositories;
        this.offline = offline;
    }

    public Set<Repository> attemptedRepositories() {
        return repositories.stream()
                // TODO: handle multiple values
                .filter(r->!offline || isOfflineRepo(r))
                .map(RepositoryUtils::toChannelRepository)
                .collect(Collectors.toSet());
    }

    public Set<Repository> offlineRepositories() {
        return repositories.stream()
                // TODO: handle multiple values
                .filter(r->offline && !isOfflineRepo(r))
                .map(RepositoryUtils::toChannelRepository)
                .collect(Collectors.toSet());
    }

    private boolean isOfflineRepo(RemoteRepository r) {
        for (String offlineProtocol : OFFLINE_REPOSITORIES) {
            if (r.getUrl().startsWith(offlineProtocol)) {
                return true;
            }
        }
        return false;
    }

    public Set<ArtifactCoordinate> failedArtifacts() {
        return Set.copyOf(((UnresolvedMavenArtifactException)getCause()).getUnresolvedArtifacts());
    }
}
