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

package org.wildfly.prospero.updates;

import org.jboss.galleon.layout.ProvisioningPlan;
import org.wildfly.prospero.api.ArtifactChange;

import java.util.List;

public class UpdateSet {

    private final ProvisioningPlan fpUpdates;
    private final List<ArtifactChange> artifactUpdates;

    public UpdateSet(ProvisioningPlan fpUpdates, List<ArtifactChange> updates) {
        this.fpUpdates = fpUpdates;
        this.artifactUpdates = updates;
    }

    public ProvisioningPlan getFpUpdates() {
        return fpUpdates;
    }

    public List<ArtifactChange> getArtifactUpdates() {
        return artifactUpdates;
    }

    public boolean isEmpty() {
        return fpUpdates.getUpdates().isEmpty() && artifactUpdates.isEmpty();
    }
}
