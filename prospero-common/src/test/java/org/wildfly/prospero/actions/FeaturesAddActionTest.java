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

package org.wildfly.prospero.actions;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.test.MetadataTestUtils;
import org.wildfly.prospero.utils.MavenUtils;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class FeaturesAddActionTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private MavenUtils mavenUtils;
    private Path repository;
    private Path installDir;

    @Before
    public void setUp() throws Exception {
        mavenUtils = new MavenUtils(MavenOptions.OFFLINE_NO_CACHE);
        installDir = temp.newFolder("test-server").toPath();
        repository = temp.newFolder("repository").toPath();
    }

    @Test
    public void isFeaturePackAvailable_featurePackNotInChannel() throws Exception {
        // setup
        // create local repository with empty manifest
        final ChannelManifest manifest = MetadataTestUtils.createManifest(null);
        mavenUtils.deploy(manifest, "org.test", "test", "1.0.0", repository.toUri().toURL());

        // add channel to the local configuration
        final Channel channel = new Channel("test-channel", "", null,
                List.of(new Repository("test", repository.toUri().toURL().toExternalForm())),
                new ChannelManifestCoordinate("org.test", "test"),
                null, null);
        MetadataTestUtils.createInstallationMetadata(installDir, manifest,
                List.of(channel));

        MetadataTestUtils.createGalleonProvisionedState(installDir, "org.wildfly:wildfly-ee-galleon-pack");
        final FeaturesAddAction featuresAddAction = new FeaturesAddAction(MavenOptions.OFFLINE_NO_CACHE, installDir, Collections.emptyList(), null);

        assertFalse(featuresAddAction.isFeaturePackAvailable("idont:exist"));
    }

    @Test
    public void isFeaturePackAvailable_featurePackNotPresentInRepository() throws Exception {
        // setup
        // create local repository with empty manifest
        final ChannelManifest manifest = MetadataTestUtils.createManifest(List.of(
                new Stream("idont", "exist", "1.0.0")
        ));
        mavenUtils.deploy(manifest, "org.test", "test", "1.0.0", repository.toUri().toURL());

        // add channel to the local configuration
        final Channel channel = new Channel("test-channel", "", null,
                List.of(new Repository("test", repository.toUri().toURL().toExternalForm())),
                new ChannelManifestCoordinate("org.test", "test"),
                null, null);
        MetadataTestUtils.createInstallationMetadata(installDir, manifest,
                List.of(channel));

        MetadataTestUtils.createGalleonProvisionedState(installDir, "org.wildfly:wildfly-ee-galleon-pack");
        final FeaturesAddAction featuresAddAction = new FeaturesAddAction(MavenOptions.OFFLINE_NO_CACHE, installDir, Collections.emptyList(), null);

        assertThrows(ArtifactResolutionException.class,
                ()-> featuresAddAction.isFeaturePackAvailable("idont:exist"));
    }

    @Test
    public void isFeaturePackAvailable_featurePackPresent() throws Exception {
        // setup
        // create local repository with empty manifest
        final ChannelManifest manifest = MetadataTestUtils.createManifest(List.of(
                new Stream("org.test", "test-fp", "1.0.0")
        ));
        mavenUtils.deploy(manifest, "org.test", "test", "1.0.0", repository.toUri().toURL());
        // add channel to the local configuration
        final Channel channel = new Channel("test-channel", "", null,
                List.of(new Repository("test", repository.toUri().toURL().toExternalForm())),
                new ChannelManifestCoordinate("org.test", "test"),
                null, null);
        MetadataTestUtils.createInstallationMetadata(installDir, manifest,
                List.of(channel));
        // deploy
        mavenUtils.deployEmptyArtifact("org.test", "test-fp", "1.0.0", null, "zip", repository.toUri().toURL());

        MetadataTestUtils.createGalleonProvisionedState(installDir, "org.wildfly:wildfly-ee-galleon-pack");
        final FeaturesAddAction featuresAddAction = new FeaturesAddAction(MavenOptions.OFFLINE_NO_CACHE, installDir, Collections.emptyList(), null);

        assertTrue(featuresAddAction.isFeaturePackAvailable("org.test:test-fp"));
    }

}