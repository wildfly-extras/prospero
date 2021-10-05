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

package com.redhat.prospero.impl.repository.curated;

import com.redhat.prospero.testing.util.MockResolver;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class CuratedMavenResolverTest {

    private MockResolver mockResolver;
    private CuratedPolicies policies;
    private CuratedMavenRepository repository;

    @Before
    public void setup() throws Exception {
        this.mockResolver = new MockResolver();
        this.policies = new CuratedPolicies();
        this.repository = new CuratedMavenRepository(mockResolver, policies);
    }

    @Test
    public void resolvesLatestMicroUpdate() throws Exception {
        mockResolver.setArtifactRange("foo:bar", Arrays.asList("1.1.1","1.1.2"));
        allow("foo:bar", CuratedPolicies.Policy.MICRO);

        final Artifact found = repository.resolveLatestVersionOf(new DefaultArtifact("foo:bar:1.1.1"));

        assertEquals("1.1.2", found.getVersion());
    }

    @Test
    public void resolveLatestMicroUpdate_IgnoresMinorUpdates() throws Exception {
        mockResolver.setArtifactRange("foo:bar", Arrays.asList("1.1.1","1.1.2", "1.2.0"));
        allow("foo:bar", CuratedPolicies.Policy.MICRO);

        final Artifact found = repository.resolveLatestVersionOf(new DefaultArtifact("foo:bar:1.1.1"));

        assertEquals("1.1.2", found.getVersion());
    }

    @Test
    public void getVersionRange_IgnoresMinorUpdates() throws Exception {
        mockResolver.setArtifactRange("foo:bar", Arrays.asList("1.1.1", "1.1.2", "1.2.0"));
        allow("foo:bar", CuratedPolicies.Policy.MICRO);

        final VersionRangeResult versionRange = repository.getVersionRange(new DefaultArtifact("foo:bar:1.1.1"));

        assertEquals(2, versionRange.getVersions().size());
        assertEquals("1.1.1", versionRange.getVersions().get(0).toString());
        assertEquals("1.1.2", versionRange.getVersions().get(1).toString());
    }

    private CuratedPolicies allow(String ga, CuratedPolicies.Policy policy) {
        policies.allow(ga, policy);
        return policies;
    }
}
