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

package org.wildfly.prospero.api;

import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class ProvisioningDefinitionTest {

    public static final String EAP_FPL = "eap-8.0-beta";

    @Test
    public void setChannelWithFileUrl() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setFpl(EAP_FPL);

        builder.setChannel("file:/tmp/foo.bar");

        assertEquals("file:/tmp/foo.bar", builder.build().getChannelRefs().get(0).getUrl());
    }

    @Test
    public void setChannelWithHttpUrl() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setFpl(EAP_FPL);

        builder.setChannel("http://localhost/foo.bar");

        assertEquals("http://localhost/foo.bar", builder.build().getChannelRefs().get(0).getUrl());
    }

    @Test
    public void setChannelWithLocalFilePath() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setFpl(EAP_FPL);

        builder.setChannel("tmp/foo.bar");

        assertEquals("file:" + Paths.get("tmp/foo.bar").toAbsolutePath(), builder.build().getChannelRefs().get(0).getUrl());
    }

    @Test
    public void overrideRemoteRepos() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder()
                .setFpl(EAP_FPL)
                .setRemoteRepositories(Arrays.asList("http://test.repo1", "http://test.repo2"));

        final ProvisioningDefinition def = builder.build();

        assertThat(def.getRepositories().stream().map(RemoteRepository::getUrl)).containsExactlyInAnyOrder(
                "http://test.repo1",
                "http://test.repo2"
        );
    }
}