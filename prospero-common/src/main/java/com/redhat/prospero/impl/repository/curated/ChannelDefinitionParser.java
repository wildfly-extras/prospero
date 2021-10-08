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
                String ga = e.getGroupId() + ":" + e.getArtifactId();
                if (e.getVersionRule().getStream() != null){
                    String policyName = e.getVersionRule().getStream();
                    ChannelRules.Policy policy;
                    switch (policyName.toUpperCase(Locale.ROOT)) {
                        case "MICRO":
                            policy = ChannelRules.Policy.MICRO;
                            break;
                        case "ANY":
                            policy = ChannelRules.Policy.ANY;
                            break;
                        default:
                            throw new IllegalArgumentException("Unknown stream policy: " + policyName);
                    }
                    curatedPolicies.getChannelRules().allow(ga, policy);
                } else {
                    throw new IllegalArgumentException("Unknown policy type");
                }
            }
        }
        return curatedPolicies;
    }
}
