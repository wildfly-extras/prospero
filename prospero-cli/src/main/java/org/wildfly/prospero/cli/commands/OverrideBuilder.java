package org.wildfly.prospero.cli.commands;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.cli.ArgumentParsingException;
import org.wildfly.prospero.cli.CliMessages;


/**
 * Creates a list of channels with appropriate overrides. Currently, supports overrides manifest versions and
 * repository definitions. If no overrides are defined, returns an empty list.
 */
class OverrideBuilder {

    private final List<Channel> channels;
    private List<Repository> shadowRepositories = Collections.emptyList();
    private List<String> versions = Collections.emptyList();

    OverrideBuilder(List<Channel> channels) {
        this.channels = channels;
    }

    static OverrideBuilder from(List<Channel> channels) {
        return new OverrideBuilder(channels);
    }

    List<Channel> build() throws ArgumentParsingException {
        final Map<String, VersionOverride> channelsMap = new HashMap<>();
        if (!versions.isEmpty()) {
            for (String version : versions) {
                final VersionOverride override = new VersionOverride(version);

                if (channelsMap.containsKey(override.channelName)) {
                    throw CliMessages.MESSAGES.duplicatedVersionOverride(override.channelName);
                }
                channelsMap.put(override.channelName, override);
            }
            final Set<String> existingChannelNames = channels.stream().map(Channel::getName).collect(Collectors.toSet());
            for (String overrideKey : channelsMap.keySet()) {
                if (!existingChannelNames.contains(overrideKey)) {
                    throw CliMessages.MESSAGES.channelNotFoundException(overrideKey);
                }
            }

            if (existingChannelNames.size() != channelsMap.size() || !existingChannelNames.containsAll(channelsMap.keySet())) {
                throw CliMessages.MESSAGES.versionOverrideHasToApplyToAllChannels();
            }
        } else if (shadowRepositories.isEmpty()) {
            // we don't need to alter the channels, can return an empty collection
            return Collections.emptyList();
        }


        final List<Channel> list = new ArrayList<>();
        for (Channel c : channels) {
            // map the channel to version override
            final VersionOverride version = channelsMap.get(c.getName());
            Channel channel = overrideChannel(c, version);
            list.add(channel);
        }
        return list;
    }

    OverrideBuilder withRepositories(List<Repository> shadowRepositories) {
        this.shadowRepositories = shadowRepositories;
        return this;
    }

    public OverrideBuilder withManifestVersions(List<String> versions) {
        this.versions = versions;
        return this;
    }

    private static class VersionOverride {
        final String channelName;
        final String version;

        public VersionOverride(String version) throws ArgumentParsingException {
            final String[] parts = version.split("::");
            if (parts.length != 2) {
                throw CliMessages.MESSAGES.invalidVersionOverrideString(version);
            }
            this.channelName = parts[0];
            this.version = parts[1];
        }
    }

    private Channel overrideChannel(Channel c, VersionOverride version) {
        final Channel.Builder builder = new Channel.Builder(c);
        if (version != null) {
            if (c.getName().equals(version.channelName)) {
                final ChannelManifestCoordinate coord = c.getManifestCoordinate();
                if (coord.getUrl() != null) {
                    try {
                        builder.setManifestUrl(URI.create(version.version).toURL());
                    } catch (MalformedURLException e) {
                        throw ProsperoLogger.ROOT_LOGGER.invalidUrl(version.version, e);
                    }
                } else {
                    builder.setManifestCoordinate(coord.getGroupId(), coord.getArtifactId(), version.version);
                }
            }
        }
        if (!shadowRepositories.isEmpty()) {
            builder.setRepositories(shadowRepositories);
        }

        return builder.build();
    }
}
