/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.galleon;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.Channel;
import org.wildfly.prospero.api.exceptions.ChannelDefinitionException;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertThrows;

@RunWith(MockitoJUnitRunner.class)
public class GalleonEnvironmentTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Mock
    private MavenSessionManager msm;

    @Test
    public void createEnvWithInvalidManifestThrowsException() throws Exception {
        final File manifest = temp.newFile();
        Files.writeString(manifest.toPath(), "schemaVersion: 1.0.0\n + foo: bar");
        final Channel build = new Channel.Builder()
                .setManifestUrl(manifest.toURI().toURL())
                .build();
        assertThrows(ChannelDefinitionException.class, ()->
            GalleonEnvironment.builder(temp.newFolder().toPath(), List.of(build), msm).build());
    }

}