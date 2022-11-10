/*
 *
 *  * Copyright 2022 Red Hat, Inc. and/or its affiliates
 *  * and other contributors as indicated by the @author tags.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.wildfly.prospero.actions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.Channel;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.model.ProsperoConfig;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MetadataActionTest {

    @Mock
    private InstallationMetadata metadata;
    private MetadataAction metadataAction;
    private ArrayList<Channel> channels;

    @Before
    public void setUp() {
        metadataAction = new MetadataAction(metadata);
        channels = new ArrayList<>();
        when(metadata.getProsperoConfig()).thenReturn(new ProsperoConfig(channels));
    }

    @Test
    public void removeNonExistingChannel() throws Exception {
        assertThrows(MetadataException.class, () -> metadataAction.removeChannel("idontexist"));
    }

    @Test
    public void addDuplicatedChannelNameThrowsException() throws Exception {
        metadataAction.addChannel(new Channel("test-1", null, null, null, null, null));
        assertThrows(MetadataException.class, ()->
            metadataAction.addChannel(new Channel("test-1", null, null, null, null, null))
        );

        assertThat(channels)
                .map(Channel::getName)
                .containsExactly("test-1");
    }
}