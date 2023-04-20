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

import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.Repository;

import java.util.Collections;
import java.util.Set;

public class ArtifactResolutionException extends OperationException {

    private Set<Repository> repositories = Collections.emptySet();
    private boolean offline;
    private Set<ArtifactCoordinate> missingArtifacts = Collections.emptySet();

    public ArtifactResolutionException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public ArtifactResolutionException(String msg, Throwable cause, Set<ArtifactCoordinate> missingArtifacts,
                                       Set<Repository> repositories, boolean offline) {
        super(msg, cause);
        this.repositories = repositories;
        this.offline = offline;
        this.missingArtifacts = missingArtifacts;
    }

    public Set<ArtifactCoordinate> getMissingArtifacts() {
        return missingArtifacts;
    }

    public Set<Repository> getRepositories() {
        return repositories;
    }

    public boolean isOffline() {
        return offline;
    }
}
