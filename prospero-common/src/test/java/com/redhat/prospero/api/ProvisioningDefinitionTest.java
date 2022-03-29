package com.redhat.prospero.api;

import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.*;

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
}