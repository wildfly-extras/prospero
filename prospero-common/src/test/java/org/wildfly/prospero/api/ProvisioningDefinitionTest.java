package org.wildfly.prospero.api;

import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class ProvisioningDefinitionTest {

    @Test
    public void setChannelWithFileUrl() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setFpl("eap");

        builder.setChannel("file:/tmp/foo.bar");

        assertEquals("file:/tmp/foo.bar", builder.build().getChannelRefs().get(0).getUrl());
    }

    @Test
    public void setChannelWithHttpUrl() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setFpl("eap");

        builder.setChannel("http://localhost/foo.bar");

        assertEquals("http://localhost/foo.bar", builder.build().getChannelRefs().get(0).getUrl());
    }

    @Test
    public void setChannelWithLocalFilePath() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setFpl("eap");

        builder.setChannel("tmp/foo.bar");

        assertEquals("file:" + Paths.get("tmp/foo.bar").toAbsolutePath(), builder.build().getChannelRefs().get(0).getUrl());
    }

    @Test
    public void overrideRemoteRepos() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder()
                .setFpl("eap")
                .setRemoteRepositories(Arrays.asList("http://test.repo1", "http://test.repo2"));

        final ProvisioningDefinition def = builder.build();

        assertThat(def.getRepositories().stream().map(RemoteRepository::getUrl)).containsExactlyInAnyOrder(
                "http://test.repo1",
                "http://test.repo2"
        );
    }
}