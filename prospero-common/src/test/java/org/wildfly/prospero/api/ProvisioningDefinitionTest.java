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

import org.assertj.core.groups.Tuple;
import org.jboss.galleon.ProvisioningException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.MavenCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.api.exceptions.ChannelDefinitionException;
import org.wildfly.prospero.api.exceptions.NoChannelException;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.net.URL;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

public class ProvisioningDefinitionTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    public static final String EAP_FPL = "known-fpl";
    private static final Tuple CENTRAL_REPO = Tuple.tuple("central", "https://repo1.maven.org/maven2/");
    private static final VersionResolverFactory VERSION_RESOLVER_FACTORY = new VersionResolverFactory(null, null);

    @Test
    public void setChannelWithFileUrl() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setProfile(EAP_FPL);

        builder.setManifest("file:/tmp/foo.bar");
        final ProvisioningDefinition definition = builder.build();
        List<Channel> channels = definition.resolveChannels(VERSION_RESOLVER_FACTORY);

        assertEquals(1, channels.size());
        assertEquals(new URL("file:/tmp/foo.bar"), channels.get(0).getManifestCoordinate().getUrl());
        assertThat(channels.get(0).getRepositories())
                .map(r-> Tuple.tuple(r.getId(), r.getUrl()))
                .containsOnly(CENTRAL_REPO);
        verifyFeaturePackLocation(definition);
    }

    @Test
    public void setChannelWithHttpUrl() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setProfile(EAP_FPL);

        builder.setManifest("http://localhost/foo.bar");
        final ProvisioningDefinition definition = builder.build();
        List<Channel> channels = definition.resolveChannels(VERSION_RESOLVER_FACTORY);

        assertEquals(1, channels.size());
        assertEquals(new URL("http://localhost/foo.bar"), channels.get(0).getManifestCoordinate().getUrl());
        assertThat(channels.get(0).getRepositories())
                .map(r-> Tuple.tuple(r.getId(), r.getUrl()))
                .containsOnly(CENTRAL_REPO);
        verifyFeaturePackLocation(definition);
    }

    @Test
    public void setChannelWithLocalFilePath() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setProfile(EAP_FPL);

        builder.setManifest("tmp/foo.bar");
        final ProvisioningDefinition definition = builder.build();
        List<Channel> channels = definition.resolveChannels(VERSION_RESOLVER_FACTORY);

        assertEquals(1, channels.size());
        assertEquals(Paths.get("tmp/foo.bar").toAbsolutePath().toUri().toURL(), channels.get(0).getManifestCoordinate().getUrl());
        assertThat(channels.get(0).getRepositories())
                .map(r-> Tuple.tuple(r.getId(), r.getUrl()))
                .containsOnly(CENTRAL_REPO);
        verifyFeaturePackLocation(definition);
    }

    @Test
    public void addAdditionalRemoteRepos() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder()
                .setProfile(EAP_FPL)
                .setOverrideRepositories(Arrays.asList(
                        new Repository("temp-repo-0", "http://test.repo1"),
                        new Repository("temp-repo-1", "http://test.repo2")));

        final ProvisioningDefinition def = builder.build();

        assertThat(def.resolveChannels(VERSION_RESOLVER_FACTORY))
                .flatMap(Channel::getRepositories)
                .map(r-> Tuple.tuple(r.getId(), r.getUrl()))
                .containsExactlyInAnyOrder(
                        tuple("temp-repo-0", "http://test.repo1"),
                        Tuple.tuple("temp-repo-1" ,"http://test.repo2")
                );
    }


    @Test
    public void customFplManifestNoRepos() throws Exception {
        try {
            final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder()
                    .setFpl("custom:fpl")
                    .setManifest("tmp/foo.bar");
            builder.build();
            fail("Expected to fail because no repositories were given.");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains(ProsperoLogger.ROOT_LOGGER.repositoriesMustBeSetWithManifest().getMessage());
        }
    }

    @Test
    public void customFplManifestRepos() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder()
                .setFpl("custom:fpl")
                .setManifest("tmp/foo.bar")
                .setOverrideRepositories(Arrays.asList(
                        new Repository("temp-repo-0", "http://test.repo1"),
                        new Repository("temp-repo-1", "http://test.repo2")));

        final ProvisioningDefinition def = builder.build();

        assertThat(def.resolveChannels(VERSION_RESOLVER_FACTORY))
                .flatMap(Channel::getRepositories)
                .map(r-> Tuple.tuple(r.getId(), r.getUrl()))
                .containsExactlyInAnyOrder(
                        tuple("temp-repo-0", "http://test.repo1"),
                        Tuple.tuple("temp-repo-1" ,"http://test.repo2")
                );
    }

    @Test
    public void knownProfileWithoutChannel() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setProfile("no-channel");

        try {
            ProvisioningDefinition definition = builder.build();
            definition.resolveChannels(VERSION_RESOLVER_FACTORY);
            fail("Building FPL without channel should fail");
        } catch (NoChannelException ignore) {
            // OK
        }
    }

    @Test
    public void knownProfileWithMultipleChannels() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setProfile("multi-channel");

        final ProvisioningDefinition def = builder.build();
        assertThat(def.resolveChannels(VERSION_RESOLVER_FACTORY).stream().map(c -> c.getManifestCoordinate().getMaven()))
                .contains(
                new MavenCoordinate("test", "one", null),
                new MavenCoordinate("test", "two", null));
    }

    @Test
    public void knownProfileWithBothManifestAndRepositories() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder()
                .setProfile("multi-channel")
                .setManifest("file:/tmp/foo.bar")
                .setOverrideRepositories(Arrays.asList(
                        new Repository("temp-repo-0", "http://test.repo1"),
                        new Repository("temp-repo-1", "http://test.repo2")));

        final ProvisioningDefinition def = builder.build();
        List<Channel> channels = def.resolveChannels(VERSION_RESOLVER_FACTORY);

        assertEquals(1, channels.size());
        final Channel channel = channels.get(0);
        assertEquals(new URL("file:/tmp/foo.bar"), channel.getManifestCoordinate().getUrl());
        assertThat(channel.getRepositories())
                .map(r-> Tuple.tuple(r.getId(), r.getUrl()))
                .containsExactlyInAnyOrder(
                        Tuple.tuple("temp-repo-0" ,"http://test.repo1"),
                        Tuple.tuple("temp-repo-1" ,"http://test.repo2")
                );
    }

    @Test
    public void knownProfileWithConfig() throws Exception {
        final File file = temp.newFile();

        MetadataTestUtils.writeChannels(file.toPath(), List.of(new Channel("test", null, null,
                List.of(new Repository("test_repo", "http://custom.repo")),
                new ChannelManifestCoordinate("new.test", "gav"),
                null, null)));

        ProvisioningDefinition def = new ProvisioningDefinition.Builder().setProfile("multi-channel")
                .setChannelCoordinates(file.toPath().toString()).build();

        VersionResolverFactory versionResolverFactory = new VersionResolverFactory(null, null);
        List<Channel> channels = def.resolveChannels(versionResolverFactory);

        assertEquals(1, channels.size());
        final Channel channel = channels.get(0);
        assertEquals(new MavenCoordinate("new.test", "gav", null), channel.getManifestCoordinate().getMaven());
        assertThat(channel.getRepositories())
                .map(r-> Tuple.tuple(r.getId(), r.getUrl()))
                .containsExactlyInAnyOrder(
                        Tuple.tuple("test_repo" ,"http://custom.repo")
                );
    }

    @Test
    public void resolveInvalidChannelThrowsException() throws Exception {
        final File channel = temp.newFile();
        Files.writeString(channel.toPath(), "schemaVersion: 2.0.0");
        final ProvisioningDefinition def = new ProvisioningDefinition.Builder().setProfile("multi-channel")
                .setChannelCoordinates(channel.toURI().toString())
                .build();

        assertThrows(ChannelDefinitionException.class, ()-> def.resolveChannels(null));
    }

    @Test
    public void unknownProfileNameThrowsException() throws Exception {
        final ProvisioningDefinition.Builder builder = new ProvisioningDefinition.Builder().setProfile("idontexist");

        assertThrows(IllegalArgumentException.class, ()-> {
            builder.build();
        });
    }

    private void verifyFeaturePackLocation(ProvisioningDefinition definition) throws ProvisioningException, XMLStreamException {
        assertNull(definition.getFpl());
        GalleonProvisioningConfig galleonConfig = GalleonUtils.loadProvisioningConfig(definition.getDefinition());
        assertEquals(1, galleonConfig.getFeaturePackDeps().size());
        assertEquals("org.wildfly.core:wildfly-core-galleon-pack:zip",
                galleonConfig.getFeaturePackDeps().iterator().next().getLocation().toString());
    }
}
