package com.redhat.prospero.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ChannelDefinitionModel {

    @JsonProperty
    private List<StreamModel> streams;
    private String repositoryUrl;

    public ChannelDefinitionModel() {

    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public List<StreamModel> getStreams() {
        return streams;
    }
}
