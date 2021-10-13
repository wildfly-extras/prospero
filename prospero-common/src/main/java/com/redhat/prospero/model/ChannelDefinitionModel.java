package com.redhat.prospero.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ChannelDefinitionModel {

    @JsonProperty
    private List<StreamModel> streams;
    @JsonProperty
    private List<RepositoryModel> repositories;

    public ChannelDefinitionModel() {

    }

    public List<RepositoryModel> getRepositories() {
        return repositories;
    }

    public List<StreamModel> getStreams() {
        return streams;
    }
}
