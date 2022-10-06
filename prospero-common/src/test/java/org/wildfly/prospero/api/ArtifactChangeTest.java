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

import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class ArtifactChangeTest {

    @Test
    public void testCompare() {
        final ArtifactChange change = change("1.0.0", "1.0.1");

        assertFalse(change.isDowngrade());
    }

    @Test
    public void testCompare2() {
        final ArtifactChange change = change("1.0.0.Beta-redhat-202201001", "1.0.0.Final-redhat-00002");

        assertFalse(change.isDowngrade());
    }

    private ArtifactChange change(String oldVersion, String newVersion) {
        return new ArtifactChange(new DefaultArtifact("org.foo", "bar", null, oldVersion),
                new DefaultArtifact("org.foo", "bar", null, newVersion));
    }

}