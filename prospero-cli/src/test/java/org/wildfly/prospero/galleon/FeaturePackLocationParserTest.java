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

package org.wildfly.prospero.galleon;

import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;


@RunWith(MockitoJUnitRunner.class)
public class FeaturePackLocationParserTest {

    @Mock
    private ChannelMavenArtifactRepositoryManager repoManager;
    private FeaturePackLocationParser parser;

    @Before
    public void setUp() throws Exception {
        parser = new FeaturePackLocationParser(repoManager);
    }

    @Test
    public void findLatestFeaturePackInPartialGav() throws Exception {
        when(repoManager.getLatestVersion(any())).thenReturn("26.1.0.Final");
        final FeaturePackLocation resolvedFpl = resolveFplVersion("org.wildfly:wildfly-ee-galleon-pack");
        assertEquals("26.1.0.Final", resolvedFpl.getBuild());
        assertEquals("org.wildfly:wildfly-ee-galleon-pack::zip", resolvedFpl.getProducerName());
    }

    @Test
    public void useProvidedGavWhenUsedFull() throws Exception {
        final FeaturePackLocation resolvedFpl = resolveFplVersion("org.wildfly:wildfly-ee-galleon-pack:26.0.0.Final");
        assertEquals("26.0.0.Final", resolvedFpl.getBuild());
        assertEquals("org.wildfly:wildfly-ee-galleon-pack::zip", resolvedFpl.getProducerName());
    }

    @Test
    public void useUniverseIfProvided() throws Exception {
        assertEquals(null, resolveFplVersion("wildfly@maven(community-universe):current").getBuild());
        assertEquals("current", resolveFplVersion("wildfly@maven(community-universe):current").getChannelName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void requireGroupAndArtifactIds() throws Exception {
        resolveFplVersion("illegalname");
    }

    private FeaturePackLocation resolveFplVersion(String fplText) throws MavenUniverseException {
        return parser.resolveFpl(fplText);
    }
}