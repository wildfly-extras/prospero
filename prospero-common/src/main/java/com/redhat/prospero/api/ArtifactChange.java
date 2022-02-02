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

package com.redhat.prospero.api;

import org.eclipse.aether.artifact.Artifact;

public class ArtifactChange {
    private Artifact oldVersion;
    private Artifact newVersion;

    public ArtifactChange(Artifact oldVersion, Artifact newVersion) {
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
    }

    public Artifact getNewVersion() {
        return newVersion;
    }

    public Artifact getOldVersion() {
        return oldVersion;
    }

    @Override
    public String toString() {
        final String gac;
        if (oldVersion.getClassifier() == null || oldVersion.getClassifier().isEmpty()) {
            gac = String.format("%s:%s", oldVersion.getGroupId(), oldVersion.getArtifactId());
        } else {
            gac = String.format("%s:%s:%s", oldVersion.getGroupId(), oldVersion.getArtifactId(), oldVersion.getClassifier());
        }
        return String.format("Update [%s]:\t\t %s ==> %s", gac, oldVersion.getVersion(), newVersion.getVersion());
    }
}
