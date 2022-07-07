/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.it;

import java.util.Collection;
import java.util.List;

import org.jboss.galleon.layout.FeaturePackUpdatePlan;
import org.jboss.galleon.progresstracking.ProgressCallback;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.api.ArtifactChange;

public class AcceptingConsole implements Console {

    @Override
    public void installationComplete() {
        // no op
    }

    @Override
    public ProgressCallback<?> getProgressCallback(String id) {
        return null;
    }

    @Override
    public void updatesFound(Collection<FeaturePackUpdatePlan> updates, List<ArtifactChange> changes) {
        // no op
    }

    @Override
    public boolean confirmUpdates() {
        return true;
    }

    @Override
    public void updatesComplete() {
        // no op
    }
}
