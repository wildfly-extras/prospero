package com.redhat.prospero.impl.repository.curated;

import org.junit.Test;

import java.io.FileWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class ChannelDefinitionParserTest {

    @Test
    public void noPolicyDefinition_defaultsToRequestedVersion() throws Exception {
        final ChannelDefinition channelDefinition = parsePolicy("");
        
        assertEquals(ChannelRules.REQUESTED_VERSION_POLICY, channelDefinition.getChannelRules().getPolicy("foo:bar"));
    }

    @Test
    public void microStreamPolicy_allowsMicroUpdates() throws Exception {
        final ChannelDefinition channelDefinition = parsePolicy(
                "{\n" +
                        "  \"streams\" : [{\n" +
                        "    \"groupId\" : \"foo\",\n" +
                        "    \"artifactId\" : \"bar\",\n" +
                        "    \"versionRule\" : {\n" +
                        "      \"stream\" : \"MICRO\"\n" +
                        "    }\n" +
                        "  }]\n" +
                        "}");

        assertEquals(ChannelRules.NamedPolicy.MICRO, channelDefinition.getChannelRules().getPolicy("foo:bar"));
    }

    @Test
    public void strictVersionPolicy_allowsUpdatesToOneVersionOnly() throws Exception {
        final ChannelDefinition channelDefinition = parsePolicy(
                "{\n" +
                        "  \"streams\" : [{\n" +
                        "    \"groupId\" : \"foo\",\n" +
                        "    \"artifactId\" : \"bar\",\n" +
                        "    \"version\" : \"1.2.3\"\n" +
                        "  }]\n" +
                        "}");

        assertEquals(ChannelRules.version("1.2.3"), channelDefinition.getChannelRules().getPolicy("foo:bar"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void versionAndVersionRule_notAllowed() throws Exception {
        parsePolicy(
    "{\n" +
            "  \"streams\" : [{\n" +
            "    \"groupId\" : \"foo\",\n" +
            "    \"artifactId\" : \"bar\",\n" +
            "    \"version\" : \"1.2.3\",\n" +
            "    \"versionRule\" : {\n" +
            "      \"stream\" : \"MICRO\"\n" +
            "    }\n" +
            "  }]\n" +
            "}");

    }

    @Test
    public void readRepositoryUrl() throws Exception {
        final ChannelDefinition channelDefinition = parsePolicy(
                "{" +
                            "\"repositories\" : [{" +
                                "\"url\" : \"http://foo.bar\"" +
                            "}]" +
                        "}");

        assertEquals(Arrays.asList("http://foo.bar"), channelDefinition.getRepositoryUrls());
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
