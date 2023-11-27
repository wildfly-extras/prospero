package org.wildfly.prospero.updates;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CandidateProperties {

    protected static final String DEFAULT_SCHEMA = "1.0.0";
    private final String schemaVersion;
    private final List<ComponentUpdate> updates;

    @JsonIgnore
    private final Map<String, String> updatesMap = new HashMap<>();

    public CandidateProperties(List<ComponentUpdate> updates) {
        this(DEFAULT_SCHEMA, updates);
    }

    @JsonCreator
    public CandidateProperties(@JsonProperty(required = true, value = "schemaVersion") String schemaVersion,
                               @JsonProperty(value = "updates") List<ComponentUpdate> updates) {
        this.schemaVersion = schemaVersion;
        this.updates = updates==null ? Collections.emptyList() : updates;
        this.updates.forEach(u->updatesMap.put(u.getGroupId() + ":" + u.getArtifactId(), u.getChannelName()));
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public List<ComponentUpdate> getUpdates() {
        return updates;
    }

    @Override
    public String toString() {
        return "CandidateProperties{" +
                "schemaVersion='" + schemaVersion + '\'' +
                ", updates=" + updates +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CandidateProperties that = (CandidateProperties) o;
        return Objects.equals(schemaVersion, that.schemaVersion) && Objects.equals(updates, that.updates);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, updates);
    }

    @JsonIgnore
    public String getUpdateChannel(String key) {
        return updatesMap.get(key);
    }

    public static class ComponentUpdate {
        private String groupId;
        private String artifactId;
        private String channelName;

        @JsonCreator
        public ComponentUpdate(@JsonProperty(value = "groupId") String groupId,
                               @JsonProperty(value = "artifactId") String artifactId,
                               @JsonProperty(value = "channelName") String channelName) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.channelName = channelName;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getChannelName() {
            return channelName;
        }

        @Override
        public String toString() {
            return "ComponentUpdate{" +
                    "groupId='" + groupId + '\'' +
                    ", artifactId='" + artifactId + '\'' +
                    ", channelName='" + channelName + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ComponentUpdate that = (ComponentUpdate) o;
            return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) && Objects.equals(channelName, that.channelName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, artifactId, channelName);
        }
    }
}
