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

import org.junit.Test;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChannelChangeTest {

    private final Channel channel1 = new Channel("channel-1", null, null,
            List.of(new Repository("repo1", "url1"), new Repository("repo2", "url2")),
            new ChannelManifestCoordinate("foo", "bar"), null, null);

    private final Channel channel2 = new Channel("channel-1", null, null,
            List.of(new Repository("repo1", "url1b"), new Repository("repo2", "url2")),
            new ChannelManifestCoordinate("foo", "bar2"), null, null);

    @Test
    public void changeWithAddedChannel() throws Exception {
        final Diff diff = ChannelChange.added(channel1);

        assertEquals("channel-1", diff.getName().get());
        final List<Diff> children = diff.getChildren();

        assertEquals(Optional.empty(), children.get(0).getOldValue());
        assertEquals("foo:bar", children.get(0).getNewValue().get());

        final List<Diff> repos = children.get(1).getChildren();
        assertEquals("repositories", children.get(1).getName().get());
        assertEquals("repo1::url1", repos.get(0).getNewValue().get());
        assertEquals(Optional.empty(), repos.get(0).getOldValue());
    }

    @Test
    public void changeWithRemovedChannel() throws Exception {
        final Diff diff = ChannelChange.removed(channel1);

        assertEquals("channel-1", diff.getName().get());
        final List<Diff> children = diff.getChildren();

        assertEquals(Optional.empty(), children.get(0).getNewValue());
        assertEquals("foo:bar", children.get(0).getOldValue().get());

        final List<Diff> repos = children.get(1).getChildren();
        assertEquals("repositories", children.get(1).getName().get());
        assertEquals("repo1::url1", repos.get(0).getOldValue().get());
        assertEquals(Optional.empty(), repos.get(0).getNewValue());
    }

    @Test
    public void changeWithModifiedChannel() throws Exception {
        final Diff diff = ChannelChange.modified(channel1, channel2);

        assertEquals("channel-1", diff.getName().get());
        final List<Diff> children = diff.getChildren();

        assertEquals("foo:bar2", children.get(0).getNewValue().get());
        assertEquals("foo:bar", children.get(0).getOldValue().get());

        final List<Diff> repos = children.get(1).getChildren();
        assertEquals("repositories", children.get(1).getName().get());
        assertEquals("repo1::url1", repos.get(0).getOldValue().get());
        assertEquals("repo1::url1b", repos.get(0).getNewValue().get());
        assertEquals(1, repos.size());
    }

    @Test
    public void changeWithSameChannels() throws Exception {
        final Diff diff = ChannelChange.modified(channel1, channel1);

        assertTrue(diff.getChildren().isEmpty());
    }

}