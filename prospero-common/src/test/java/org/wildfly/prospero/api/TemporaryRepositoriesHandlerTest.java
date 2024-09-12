/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.api;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TemporaryRepositoriesHandlerTest {

    @Test
    public void emptyListReturnsEmptyList() {
        final List<Channel> channels = applyOverride(Collections.emptyList(), Collections.emptyList());

        assertThat(channels).isEmpty();
    }

    @Test
    public void emptyRepositoriesUnchangedChannels() {
        final List<Channel> originalChannels = List.of(getChannel("channel-0", repo("repo-0", "http://test.te")));
        final List<Channel> channels = applyOverride(originalChannels, Collections.emptyList());

        Assert.assertNotSame("The resulting list should be a clone of original", channels, originalChannels);
        assertChannelNamesContainsExactly(channels, "channel-0");
        assertRepositoryIdContainsExactly(channels, "repo-0");
        assertRepositoryUrlContainsExactly(channels, "http://test.te");
    }

    @Test
    public void addRepositoryToSingleChannel() {
        final List<Channel> originalChannels = List.of(getChannel("channel-0", repo("repo-0", "http://test.te")));
        final List<Channel> channels = applyOverride(originalChannels, List.of(repo("temp-0", "http://temp.te")));

        Assert.assertNotSame("The resulting list should be a clone of original", channels, originalChannels);
        assertChannelNamesContainsExactly(channels,"channel-0");
        assertRepositoryIdContainsExactly(channels, "temp-0");
        assertRepositoryUrlContainsExactly(channels, "http://temp.te");
    }

    @Test
    public void addRepositoryToMultipleChannels() {
        final List<Channel> originalChannels = List.of(getChannel("channel-0", repo("repo-0", "http://test.te")),
                getChannel("channel-1", repo("repo-1", "http://test1.te")));
        final List<Channel> channels = applyOverride(originalChannels, List.of(repo("temp-0", "http://temp.te")));

        Assert.assertNotSame("The resulting list should be a clone of original", channels, originalChannels);
        assertChannelNamesContainsExactly(channels,"channel-0", "channel-1");
        assertRepositoryIdContainsExactly(channels, "temp-0", "temp-0");
        assertRepositoryUrlContainsExactly(channels, "http://temp.te", "http://temp.te");
    }

    @Test
    public void gpgCheckIsDisabled() {
        final Channel channel = new Channel.Builder()
                .setName("test-channel")
                .setGpgCheck(true)
                .build();

        final List<Channel> result = applyOverride(List.of(channel), List.of(repo("temp-0", "http://temp.te")));
        assertThat(result)
                .map(Channel::isGpgCheck)
                .containsExactly(false);
    }

    private static void assertRepositoryIdContainsExactly(List<Channel> channels, String... ids) {
        assertThat(channels)
                .flatMap(Channel::getRepositories)
                .map(Repository::getId)
                .containsExactly(ids);
    }

    private static void assertRepositoryUrlContainsExactly(List<Channel> channels, String... urls) {
        assertThat(channels)
                .flatMap(Channel::getRepositories)
                .map(Repository::getUrl)
                .containsExactly(urls);
    }

    private static void assertChannelNamesContainsExactly(List<Channel> channels, String... names) {
        assertThat(channels)
                .map(Channel::getName)
                .containsExactly(names);
    }

    private static Channel getChannel(String channelName, Repository... repos) {
        return new Channel(channelName, null, null,
                List.of(repos), null, null, null);
    }

    private static Repository repo(String id, String url) {
        return new Repository(id, url);
    }

    private static List<Channel> applyOverride(List<Channel> originalChannels, List<Repository> overrideRepositories) {
        return TemporaryRepositoriesHandler.overrideRepositories(originalChannels, overrideRepositories);
    }
}