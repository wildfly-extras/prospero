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

import java.net.URL;
import java.nio.file.Path;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;
import org.wildfly.channel.maven.ChannelCoordinate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class ArtifactUtilsTest {

    @Test
    public void testPathChannelCoordinate() {
        assertThat(ArtifactUtils.channelCoordFromString("test")).satisfies(channelCoordinate -> {
            assertThat(channelCoordinate.getUrl()).isEqualTo(Path.of("test").toAbsolutePath().toUri().toURL());
            assertThat(channelCoordinate.getGroupId()).isNull();
            assertThat(channelCoordinate.getArtifactId()).isNull();
            assertThat(channelCoordinate.getVersion()).isNull();
        });
    }

    @Test
    public void testUrlChannelCoordinate() {
        assertThat(ArtifactUtils.channelCoordFromString("http://wildfly.org:80")).satisfies(channelCoordinate -> {
            assertThat(channelCoordinate.getUrl()).isEqualTo(new URL("http://wildfly.org:80"));
            assertThat(channelCoordinate.getGroupId()).isNull();
            assertThat(channelCoordinate.getArtifactId()).isNull();
            assertThat(channelCoordinate.getVersion()).isNull();
        });
    }

    @Test
    public void testGaChannelCoordinate() {
        assertThat(ArtifactUtils.channelCoordFromString("g:a")).satisfies(channelCoordinate -> {
            assertThat(channelCoordinate.getUrl()).isNull();
            assertThat(channelCoordinate.getGroupId()).isEqualTo("g");
            assertThat(channelCoordinate.getArtifactId()).isEqualTo("a");
            assertThat(channelCoordinate.getVersion()).isNull();
        });
    }

    @Test
    public void testGavChannelCoordinate() {
        assertThat(ArtifactUtils.channelCoordFromString("g:a:v")).satisfies(channelCoordinate -> {
            assertThat(channelCoordinate.getUrl()).isNull();
            assertThat(channelCoordinate.getGroupId()).isEqualTo("g");
            assertThat(channelCoordinate.getArtifactId()).isEqualTo("a");
            assertThat(channelCoordinate.getVersion()).isEqualTo("v");
        });
    }


    @Test
    public void testPathManifestCoordinate() {
        assertThat(ArtifactUtils.manifestCoordFromString("test")).satisfies(channelCoordinate -> {
            assertThat(channelCoordinate.getUrl()).isEqualTo(Path.of("test").toAbsolutePath().toUri().toURL());
            assertThat(channelCoordinate.getGroupId()).isNull();
            assertThat(channelCoordinate.getArtifactId()).isNull();
            assertThat(channelCoordinate.getVersion()).isNull();
        });
    }

    @Test
    public void testUrlManifestCoordinate() {
        assertThat(ArtifactUtils.manifestCoordFromString("http://wildfly.org:80")).satisfies(channelCoordinate -> {
            assertThat(channelCoordinate.getUrl()).isEqualTo(new URL("http://wildfly.org:80"));
            assertThat(channelCoordinate.getGroupId()).isNull();
            assertThat(channelCoordinate.getArtifactId()).isNull();
            assertThat(channelCoordinate.getVersion()).isNull();
        });
    }

    @Test
    public void testGaManifestCoordinate() {
        assertThat(ArtifactUtils.manifestCoordFromString("g:a")).satisfies(channelCoordinate -> {
            assertThat(channelCoordinate.getUrl()).isNull();
            assertThat(channelCoordinate.getGroupId()).isEqualTo("g");
            assertThat(channelCoordinate.getArtifactId()).isEqualTo("a");
            assertThat(channelCoordinate.getVersion()).isNull();
        });
    }

    @Test
    public void testGavManifestCoordinate() {
        assertThat(ArtifactUtils.manifestCoordFromString("g:a:v")).satisfies(channelCoordinate -> {
            assertThat(channelCoordinate.getUrl()).isNull();
            assertThat(channelCoordinate.getGroupId()).isEqualTo("g");
            assertThat(channelCoordinate.getArtifactId()).isEqualTo("a");
            assertThat(channelCoordinate.getVersion()).isEqualTo("v");
        });
    }

    @Test
    public void testWindowsPath() {
        ChannelCoordinate channelCoordinate = ArtifactUtils.channelCoordFromString(
                "D:\\some\\file\\path");
        assertThat(channelCoordinate.getUrl()).isNotNull();
    }

    @Test
    public void testUnixPath() {
        assumeTrue(SystemUtils.IS_OS_UNIX); // ignore on Windows - ':' is invalid path character

        ChannelCoordinate channelCoordinate = ArtifactUtils.channelCoordFromString(
                "some/path/g:a");
        assertThat(channelCoordinate.getUrl()).isNotNull();
    }
}
