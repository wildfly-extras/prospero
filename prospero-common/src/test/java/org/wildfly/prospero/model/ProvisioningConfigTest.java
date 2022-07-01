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

package org.wildfly.prospero.model;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;


import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

public class ProvisioningConfigTest {

    private final ProvisioningConfig provisioningConfig = new ProvisioningConfig(new ArrayList<>(), new ArrayList<>());

    @Test
    public void addRepositoryIgnoresChangesIfExistingRepo() throws Exception {
        provisioningConfig.addRepository(new RepositoryRef("existing", "file:///foo.bar"));

        assertFalse(provisioningConfig.addRepository(new RepositoryRef("existing", "file:///foo.bar")));

        assertThat(provisioningConfig.getRepositories()).containsExactly(
                new RepositoryRef("existing", "file:///foo.bar")
        );
    }

    @Test
    public void addRepositoryThrowsErrorIfSameIdDifferentUrl() throws Exception {
        assertTrue(provisioningConfig.addRepository(new RepositoryRef("existing", "file:///foo.bar")));

        try {
            provisioningConfig.addRepository(new RepositoryRef("existing", "file:///different.url"));
            Assert.fail("Adding two repositories with same ID and different URL should fail");
        } catch (IllegalArgumentException e) {
            // OK, ignore
        }
    }

    @Test
    public void addRepositoryAddsDistinctRepository() throws Exception {
        provisioningConfig.addRepository(new RepositoryRef("existing", "file:///foo.bar"));

        assertTrue(provisioningConfig.addRepository(new RepositoryRef("test", "file:///foo.bar")));

        assertThat(provisioningConfig.getRepositories()).containsExactlyInAnyOrder(
                new RepositoryRef("existing", "file:///foo.bar"),
                new RepositoryRef("test", "file:///foo.bar")
        );
    }

    @Test
    public void addRepositoryAddsNewRepositoryToEmptyList() throws Exception {
        assertTrue(provisioningConfig.addRepository(new RepositoryRef("test", "file:///foo.bar")));

        assertThat(provisioningConfig.getRepositories()).containsExactly(
                new RepositoryRef("test", "file:///foo.bar")
        );
    }
}