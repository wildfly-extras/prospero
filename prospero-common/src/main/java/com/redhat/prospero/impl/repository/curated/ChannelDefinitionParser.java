package com.redhat.prospero.impl.repository.curated;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.Map;

public class ChannelDefinitionParser {

    public ChannelDefinition parsePolicyFile(URL policyFile) throws IOException {
        if (policyFile == null) {
            return new ChannelDefinition();
        }
        ObjectMapper mapper = new ObjectMapper();
        ChannelDefinitionModel data = mapper.readValue(policyFile, ChannelDefinitionModel.class);

        final ChannelDefinition curatedPolicies = new ChannelDefinition();
        curatedPolicies.setRepositoryUrl(data.getRepositoryUrl());

        if (data.getRules() != null) {
            for (Map.Entry<String, Object> e : data.getRules().entrySet()) {
                final Map<String, String> rule = (Map) e.getValue();
                if (rule.containsKey("STREAM")) {
                    String policyName = rule.get("STREAM");
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
                    curatedPolicies.getChannelRules().allow(e.getKey(), policy);
                } else {
                    throw new IllegalArgumentException("Unknown policy type");
                }
            }
        }
        return curatedPolicies;
    }
}
