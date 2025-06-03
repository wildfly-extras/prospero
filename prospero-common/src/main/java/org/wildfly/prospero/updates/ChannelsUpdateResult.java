package org.wildfly.prospero.updates;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.wildfly.prospero.api.ChannelVersion;

public class ChannelsUpdateResult {

    private final Map<String, Set<ChannelVersion>> channelVersions = new HashMap<>();
    private final Map<String, String> currentVersions = new HashMap<>();
    private final Set<String> unsupportedChannels = new HashSet<>();

    public void addChannelVersions(String channelName, String originalVersion, Collection<ChannelVersion> versions) {
        final Set<ChannelVersion> recorded = channelVersions.computeIfAbsent(channelName, (name)->new TreeSet<>());
        recorded.addAll(versions);
        currentVersions.put(channelName, originalVersion);
    }

    public void addUnsupportedChannel(String unsupportedChannel) {
        unsupportedChannels.add(unsupportedChannel);
    }

    public Set<String> getUpdatedChannels() {
        return channelVersions.keySet();
    }

    public Set<ChannelVersion> getUpdatedVersion(String channel) {
        return channelVersions.getOrDefault(channel, Collections.emptySet());
    }

    public Set<String> getUnsupportedChannels() {
        return unsupportedChannels;
    }

    public String getOriginalVersions(String channelName) {
        return currentVersions.get(channelName);
    }

    public boolean hasUpdates() {
        return !this.channelVersions.values().stream().allMatch(Collection::isEmpty);
    }
}
