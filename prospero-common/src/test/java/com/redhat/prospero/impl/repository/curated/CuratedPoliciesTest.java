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

import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionScheme;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class CuratedPoliciesTest {

    private VersionScheme versionScheme = new GenericVersionScheme();

    @Test
    public void microPolicyFiltersMicroUpdates() {
        final Stream<Version> versions = asVersions("1.1.1", "1.1.2", "1.2.1");

        final List<Version> res = filterVersions(versions, "1.1.1");

        assertEquals(2, res.size());
        assertEquals("1.1.1", res.get(0).toString());
        assertEquals("1.1.2", res.get(1).toString());
    }

    @Test
    public void microPolicyFiltersMicroUpdatesIn12Stream() {
        final Stream<Version> versions = asVersions("1.1.1", "1.1.2", "1.2.1");

        final List<Version> res = filterVersions(versions, "1.2.0");

        assertEquals(1, res.size());
        assertEquals("1.2.1", res.get(0).toString());
    }

    private List<Version> filterVersions(Stream<Version> versions, String baseVersion) {
        final Predicate<? super Version> filter = CuratedPolicies.Policy.MICRO.getFilter(baseVersion);

        return versions.filter(filter).collect(Collectors.toList());
    }

    private Stream<Version> asVersions(String... strings) {
        return Arrays.asList(strings).stream().map(this::toVersion);
    }

    private Version toVersion(String v) {
        try {
            return versionScheme.parseVersion(v);
        } catch (InvalidVersionSpecificationException e) {
            throw new RuntimeException(e);
        }
    }
}
