package com.redhat.prospero.impl.repository.curated;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.prospero.model.ChannelDefinitionModel;
import com.redhat.prospero.model.StreamModel;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;

public class ChannelDefinitionParser {

    public ChannelDefinition parsePolicyFile(URL policyFile) throws IOException {
        if (policyFile == null) {
            return new ChannelDefinition();
        }
        ObjectMapper mapper = new ObjectMapper();
        ChannelDefinitionModel data = mapper.readValue(policyFile, ChannelDefinitionModel.class);

        final ChannelDefinition curatedPolicies = new ChannelDefinition();
        curatedPolicies.setRepositoryUrl(data.getRepositoryUrl());

        if (data.getStreams() != null) {
            for (StreamModel e : data.getStreams()) {
                e.validateStream();

                String ga = e.getGroupId() + ":" + e.getArtifactId();

                if (e.getVersionRule() != null && e.getVersionRule().getStream() != null) {
                    String policyName = e.getVersionRule().getStream();
                    ChannelRules.NamedPolicy policy;
                    switch (policyName.toUpperCase(Locale.ROOT)) {
                        case "MICRO":
                            policy = ChannelRules.NamedPolicy.MICRO;
                            break;
                        case "ANY":
                            policy = ChannelRules.NamedPolicy.ANY;
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown stream policy: " + policyName);
                    }
                    curatedPolicies.getChannelRules().allow(ga, policy);
                } else if (e.getVersion() != null) {
                    curatedPolicies.getChannelRules().allow(ga, ChannelRules.version(e.getVersion()));
                } else {
                    throw new IllegalArgumentException("Unknown policy type");
                }
            }
        }
        return curatedPolicies;
    }

}
