/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.prospero.api;

import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.wfchannel.ChannelRefUpdater;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ChanelRefUpdaterTest {

    @Test(expected = ArtifactResolutionException.class)
    public void resolveChannelFileThrowsExceptionIfNoVersionsFound() throws Exception {
        // repository "test", this.getClass().getResource("/").toString(),
        final List<RemoteRepository> repositories = Arrays.asList(
                new RemoteRepository.Builder("test", "default", this.getClass().getResource("/").toString())
                        .build());
        final List<ChannelRef> channels = Arrays.asList(new ChannelRef("org.test:test:1.0", null));
        new ChannelRefUpdater(new MavenSessionManager()).resolveLatest(channels, repositories);
    }
}