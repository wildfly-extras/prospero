package com.redhat.prospero.impl.repository;

import com.redhat.prospero.api.Channel;
import com.redhat.prospero.api.Repository;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.Collections;

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

@RunWith(MockitoJUnitRunner.class)
public class MavenResolverTest {

    @Mock
    private RepositorySystem repositorySystem;
    @Captor
    private ArgumentCaptor<VersionRangeRequest> rangeCaptor;
    private Repository mavenResolver;

    @Before
    public void setUp() {
        mavenResolver = new MavenRepository(repositorySystem, Collections.<Channel>emptyList());
    }

    @Test
    public void findLatest_searchesRangeStartingWithVersion() throws Exception {
        Mockito.when(repositorySystem.resolveVersionRange(Mockito.any(), Mockito.any())).then(new Answer<VersionRangeResult>() {
            @Override
            public VersionRangeResult answer(InvocationOnMock invocationOnMock) throws Throwable {
                final VersionRangeRequest argument = invocationOnMock.getArgument(1, VersionRangeRequest.class);
                return new VersionRangeResult(argument);
            }
        });

        mavenResolver.resolveLatestVersionOf(new DefaultArtifact("group", "artifact", "", "jar", "1.0"));

        Mockito.verify(repositorySystem).resolveVersionRange(Mockito.any(), rangeCaptor.capture());
        Assert.assertEquals("[1.0,)", rangeCaptor.getValue().getArtifact().getVersion());
    }
}
