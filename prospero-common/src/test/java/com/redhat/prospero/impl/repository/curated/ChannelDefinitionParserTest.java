package com.redhat.prospero.impl.repository.curated;

import org.junit.Test;

import java.io.FileWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class ChannelDefinitionParserTest {

    @Test
    public void emptyPolicy_allowsAllUpdates() throws Exception {
        final ChannelDefinition channelDefinition = parsePolicy("");
        
        assertEquals(ChannelRules.Policy.ANY, channelDefinition.getChannelRules().getPolicy("foo:bar"));
    }

    @Test
    public void microStreamPolicy_allowsMicroUpdates() throws Exception {
        final ChannelDefinition channelDefinition = parsePolicy(
                "{" +
                "  \"rules\": {" +
                "    \"foo:bar\": {" +
                "       \"STREAM\": \"MICRO\"" +
                "    }" +
                "  }" +
                "}");

        assertEquals(ChannelRules.Policy.MICRO, channelDefinition.getChannelRules().getPolicy("foo:bar"));
    }

    @Test
    public void readRepositoryUrl() throws Exception {
        final ChannelDefinition channelDefinition = parsePolicy(
                "{" +
                            "\"repositoryUrl\":\"http://foo.bar\"" +
                        "}");

        assertEquals("http://foo.bar", channelDefinition.getRepositoryUrl());
    }

    private ChannelDefinition parsePolicy(String policyBody) throws Exception {
        URL definitionUrl;
        if (!policyBody.isEmpty()) {
            Path tempFile = Files.createTempFile("policy", "json");
            try (FileWriter fw = new FileWriter(tempFile.toFile())) {
                fw.write(policyBody);
            }
            definitionUrl = tempFile.toUri().toURL();
        } else {
            definitionUrl = null;
        }

        return new ChannelDefinitionParser().parsePolicyFile(definitionUrl);
    }
}
