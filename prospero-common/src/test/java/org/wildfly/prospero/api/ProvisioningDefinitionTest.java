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

import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;
import org.wildfly.prospero.api.exceptions.NoChannelException;
import org.wildfly.prospero.model.ChannelRef;

import java.nio.file.Paths;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ProvisioningDefinitionTest {

    public static final String EAP_FPL = "known-fpl";

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

        assertEquals(Paths.get("tmp/foo.bar").toAbsolutePath().toUri().toURL().toString(), builder.build().getChannelRefs().get(0).getUrl());
    }

    @Test
    public void overrideRemoteRepos() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder()
                .setFpl(EAP_FPL)
                .setRemoteRepositories(Arrays.asList("http://test.repo1", "http://test.repo2"));

        final ProvisioningDefinition def = builder.build();

        assertThat(def.getRepositories().stream().map(RemoteRepository::getUrl)).containsExactlyInAnyOrder(
                "http://test.repo1",
                "http://test.repo2");
    }

    @Test
    public void knownFplWithoutChannel() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setFpl("no-channel");

        try {
            builder.build();
            fail("Building FPL without channel should fail");
        } catch (NoChannelException e) {
            // OK
        }
    }

    @Test
    public void knownFplWithMultipleChannels() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setFpl("multi-channel");

        final ProvisioningDefinition def = builder.build();
        assertThat(def.getChannelRefs().stream().map(ChannelRef::getGav)).contains(
                "test:one",
                "test:two"
        );
    }
}