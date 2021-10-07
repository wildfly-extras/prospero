package com.redhat.prospero.impl.repository.curated;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ChannelDefinitionModel {

    @JsonProperty
    private Map<String, Object> rules;
    private String repositoryUrl;

    public ChannelDefinitionModel() {

    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public Map<String, Object> getRules() {
        return rules;
    }
}
