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

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.deployment.DeploymentException;
import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.creator.FeaturePackCreator;
import org.jboss.galleon.repo.RepositoryArtifactResolver;
import org.jboss.galleon.spec.ConfigLayerSpec;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.repo.SimplisticMavenRepoManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.test.MetadataTestUtils;
import org.wildfly.prospero.utils.MavenUtils;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.wildfly.prospero.metadata.ProsperoMetadataUtils.METADATA_DIR;

@RunWith(MockitoJUnitRunner.class)
public class FeaturesAddActionTest {

    private static final String NO_MODEL = null;
    private static final String NO_CONFIG = null;
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    @Mock
    private ApplyCandidateAction applyCandidateAction;
    @Mock
    private PrepareCandidateAction prepareCandidateAction;
    private MavenUtils mavenUtils;
    private Path repository;
    private URL repositoryUrl;
    private Path installDir;
    private RepositoryArtifactResolver repo;
    private FeaturesAddAction.CandidateActionsFactory candidateActionsFactory;

    @Before
    public void setUp() throws Exception {
        mavenUtils = new MavenUtils(MavenOptions.OFFLINE_NO_CACHE);
        installDir = temp.newFolder("test-server").toPath();
        repository = temp.newFolder("repository").toPath();
        repositoryUrl = repository.toUri().toURL();

        repo = SimplisticMavenRepoManager.getInstance(repository);

        candidateActionsFactory = new FeaturesAddAction.CandidateActionsFactory() {

            @Override
            public PrepareCandidateAction newPrepareCandidateActionInstance(MavenSessionManager mavenSessionManager, ProsperoConfig prosperoConfig) throws OperationException {
                return prepareCandidateAction;
            }

            @Override
            public ApplyCandidateAction newApplyCandidateActionInstance(Path candidateDir) throws ProvisioningException, OperationException {
                return applyCandidateAction;
            }
        };
    }

    @Test
    public void isFeaturePackAvailable_featurePackNotInChannel() throws Exception {
        // setup
        // create local repository with empty manifest
        final ChannelManifest manifest = MetadataTestUtils.createManifest(null);
        mavenUtils.deploy(manifest, "org.test", "test", "1.0.0", repositoryUrl);

        // add channel to the local configuration
        final Channel channel = new Channel("test-channel", "", null,
                List.of(new Repository("test", repositoryUrl.toExternalForm())),
                new ChannelManifestCoordinate("org.test", "test"),
                null, null);
        MetadataTestUtils.createInstallationMetadata(installDir, manifest,
                List.of(channel));

        MetadataTestUtils.createGalleonProvisionedState(installDir, "org.wildfly:wildfly-ee-galleon-pack");

        assertFalse(getFeaturesAddAction().isFeaturePackAvailable("idont:exist"));
    }

    @Test
    public void isFeaturePackAvailable_featurePackNotPresentInRepository() throws Exception {
        // setup
        // create local repository with empty manifest
        final ChannelManifest manifest = MetadataTestUtils.createManifest(List.of(
                new Stream("idont", "exist", "1.0.0")
        ));
        mavenUtils.deploy(manifest, "org.test", "test", "1.0.0", repositoryUrl);

        // add channel to the local configuration
        final Channel channel = new Channel("test-channel", "", null,
                List.of(new Repository("test", repositoryUrl.toExternalForm())),
                new ChannelManifestCoordinate("org.test", "test"),
                null, null);
        MetadataTestUtils.createInstallationMetadata(installDir, manifest,
                List.of(channel));

        MetadataTestUtils.createGalleonProvisionedState(installDir, "org.wildfly:wildfly-ee-galleon-pack");

        assertThrows(ArtifactResolutionException.class,
                ()-> getFeaturesAddAction().isFeaturePackAvailable("idont:exist"));
    }

    @Test
    public void isFeaturePackAvailable_featurePackPresent() throws Exception {
        // setup
        // create local repository with empty manifest
        final ChannelManifest manifest = MetadataTestUtils.createManifest(List.of(
                new Stream("org.test", "test-fp", "1.0.0")
        ));
        mavenUtils.deploy(manifest, "org.test", "test", "1.0.0", repositoryUrl);
        // add channel to the local configuration
        final Channel channel = new Channel("test-channel", "", null,
                List.of(new Repository("test", repositoryUrl.toExternalForm())),
                new ChannelManifestCoordinate("org.test", "test"),
                null, null);
        MetadataTestUtils.createInstallationMetadata(installDir, manifest,
                List.of(channel));
        // deploy
        mavenUtils.deployEmptyArtifact("org.test", "test-fp", "1.0.0", null, "zip", repositoryUrl);

        MetadataTestUtils.createGalleonProvisionedState(installDir, "org.wildfly:wildfly-ee-galleon-pack");

        assertTrue(getFeaturesAddAction().isFeaturePackAvailable("org.test:test-fp"));
    }

    @Test
    public void layerNotAvailable_throwsException() throws Exception {
        // install base feature pack
        final FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(repo);
        creator.newFeaturePack(FeaturePackLocation.fromString("org.test:base-pack:1.0.0:zip").getFPID())
            .getCreator()
            .newFeaturePack(FeaturePackLocation.fromString("org.test:added-pack:1.0.0:zip").getFPID())
                .addDependency(FeaturePackLocation.fromString("org.test:base-pack:1.0.0"))
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("standalone")
                        .setName("layer1")
                        .build());
        deployFeaturePacks(creator);
        // install
        installFeaturePack(installDir, "org.test:base-pack:1.0.0:zip");

        assertThatThrownBy(()-> getFeaturesAddAction().addFeaturePack("org.test:added-pack", Set.of("idontexist"), null, null))
                .isInstanceOf(FeaturesAddAction.LayerNotFoundException.class)
                .hasFieldOrPropertyWithValue("layer", "idontexist")
                .hasFieldOrPropertyWithValue("supportedLayers", Set.of("layer1"));

        verifyNoInteractions(prepareCandidateAction);
        verifyNoInteractions(applyCandidateAction);
    }

    @Test
    public void noLayersInTheFeaturePacks_provisionsNoConfigs() throws Exception {
        // install base feature pack
        final FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(repo);
        creator.newFeaturePack(FeaturePackLocation.fromString("org.test:base-pack:1.0.0:zip").getFPID())
                .getCreator()
                .newFeaturePack(FeaturePackLocation.fromString("org.test:added-pack:1.0.0:zip").getFPID())
                    .addDependency(FeaturePackLocation.fromString("org.test:base-pack:1.0.0"))
                        ;
        deployFeaturePacks(creator);
        // install
        installFeaturePack(installDir, "org.test:base-pack:1.0.0:zip");

        getFeaturesAddAction().addFeaturePack("org.test:added-pack", Collections.emptySet(), null, null);

        final ArgumentCaptor<ProvisioningConfig> provisioningConfigArgumentCaptor = ArgumentCaptor.forClass(ProvisioningConfig.class);
        verify(prepareCandidateAction).buildCandidate(any(), any(), eq(ApplyCandidateAction.Type.FEATURE_ADD),
                provisioningConfigArgumentCaptor.capture());

        final ProvisioningConfig config = provisioningConfigArgumentCaptor.getValue();
        assertThat(config.getDefinedConfigs()).isEmpty();
    }

    @Test
    public void noLayersInTheFeaturePackWithRequriedLayer_throwsException() throws Exception {
        // install base feature pack
        final FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(repo);
        creator.newFeaturePack(FeaturePackLocation.fromString("org.test:base-pack:1.0.0:zip").getFPID())
                .getCreator()
                .newFeaturePack(FeaturePackLocation.fromString("org.test:added-pack:1.0.0:zip").getFPID())
                    .addDependency(FeaturePackLocation.fromString("org.test:base-pack:1.0.0"))
        ;
        deployFeaturePacks(creator);
        // install
        installFeaturePack(installDir, "org.test:base-pack:1.0.0:zip");

        assertThatThrownBy(()-> getFeaturesAddAction().addFeaturePack("org.test:added-pack", Set.of("idontexist"), null, null))
                .isInstanceOf(FeaturesAddAction.LayerNotFoundException.class)
                .hasFieldOrPropertyWithValue("layer", "idontexist");

        verifyNoInteractions(prepareCandidateAction);
        verifyNoInteractions(applyCandidateAction);
    }

    @Test
    public void requestedLayerAddsItsConfig() throws Exception {
        // install base feature pack
        final FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(repo);
        creator.newFeaturePack(FeaturePackLocation.fromString("org.test:base-pack:1.0.0:zip").getFPID())
                .getCreator()
                .newFeaturePack(FeaturePackLocation.fromString("org.test:added-pack:1.0.0:zip").getFPID())
                    .addDependency(FeaturePackLocation.fromString("org.test:base-pack:1.0.0"))
                    .addConfigLayer(ConfigLayerSpec.builder()
                            .setModel("model")
                            .setName("layer1")
                            .build());

        deployFeaturePacks(creator);
        // install
        installFeaturePack(installDir, "org.test:base-pack:1.0.0:zip");

        getFeaturesAddAction().addFeaturePack("org.test:added-pack", Set.of("layer1"), null, null);

        final ArgumentCaptor<ProvisioningConfig> provisioningConfigArgumentCaptor = ArgumentCaptor.forClass(ProvisioningConfig.class);
        verify(prepareCandidateAction).buildCandidate(any(), any(), eq(ApplyCandidateAction.Type.FEATURE_ADD),
                provisioningConfigArgumentCaptor.capture());

        final ProvisioningConfig config = provisioningConfigArgumentCaptor.getValue();
        assertThat(config.getDefinedConfigs())
                .contains(ConfigModel.builder("model", "model.xml")
                        .includeLayer("layer1")
                        .build());
    }

    @Test
    public void selectedConfigOverridesDefaultWithRequestedLayer() throws Exception {
        // install base feature pack
        final FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(repo);
        creator.newFeaturePack(FeaturePackLocation.fromString("org.test:base-pack:1.0.0:zip").getFPID())
                .getCreator()
                .newFeaturePack(FeaturePackLocation.fromString("org.test:added-pack:1.0.0:zip").getFPID())
                .addDependency(FeaturePackLocation.fromString("org.test:base-pack:1.0.0"))
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("model")
                        .setName("layer1")
                        .build());

        deployFeaturePacks(creator);
        // install
        installFeaturePack(installDir, "org.test:base-pack:1.0.0:zip");

        getFeaturesAddAction().addFeaturePack("org.test:added-pack", Set.of("layer1"), null, "test.xml");

        final ArgumentCaptor<ProvisioningConfig> provisioningConfigArgumentCaptor = ArgumentCaptor.forClass(ProvisioningConfig.class);
        verify(prepareCandidateAction).buildCandidate(any(), any(), eq(ApplyCandidateAction.Type.FEATURE_ADD),
                provisioningConfigArgumentCaptor.capture());

        final ProvisioningConfig config = provisioningConfigArgumentCaptor.getValue();
        assertThat(config.getDefinedConfigs())
                .contains(ConfigModel.builder("model", "test.xml")
                        .includeLayer("layer1")
                        .build());
    }

    @Test
    public void addFeaturePackAlreadyInstalledAsDependency() throws Exception {
        // install base feature pack
        final FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(repo);
        creator.newFeaturePack(FeaturePackLocation.fromString("org.test:base-pack:1.0.0:zip").getFPID())
                .getCreator()
                    .newFeaturePack(FeaturePackLocation.fromString("org.test:added-pack:1.0.0:zip").getFPID())
                    .addConfigLayer(ConfigLayerSpec.builder()
                            .setModel("model")
                            .setName("layer1")
                            .build())
                    .addConfigLayer(ConfigLayerSpec.builder()
                            .setModel("model")
                            .setName("layer2")
                            .build());
        deployFeaturePacks(creator);

        // install
        getPm(installDir).provision(ProvisioningConfig.builder()
                        .addFeaturePackDep(FeaturePackLocation.fromString("org.test:base-pack:1.0.0"))
                        .addFeaturePackDep(FeaturePackLocation.fromString("org.test:added-pack:1.0.0"))
                        .addConfig(ConfigModel.builder("model", "test.xml")
                                .includeLayer("layer1")
                                .build())
                .build());
        mockInstallationData(installDir, "org.test:base-pack:1.0.0", "org.test:added-pack:1.0.0");

        getFeaturesAddAction().addFeaturePack("org.test:added-pack", Set.of("layer2"), null, "test.xml");

        final ArgumentCaptor<ProvisioningConfig> provisioningConfigArgumentCaptor = ArgumentCaptor.forClass(ProvisioningConfig.class);
        verify(prepareCandidateAction).buildCandidate(any(), any(), eq(ApplyCandidateAction.Type.FEATURE_ADD),
                provisioningConfigArgumentCaptor.capture());

        final ProvisioningConfig config = provisioningConfigArgumentCaptor.getValue();
        assertThat(config.getDefinedConfigs())
                .contains(ConfigModel.builder("model", "test.xml")
                        .includeLayer("layer1")
                        .includeLayer("layer2")
                        .build());
        assertThat(config.getFeaturePackDeps())
                .map(FeaturePackConfig::getLocation)
                .contains(
                        FeaturePackLocation.fromString("org.test:base-pack:zip"),
                        FeaturePackLocation.fromString("org.test:added-pack:zip"));
    }

    @Test
    public void installingLayerOverridesExcludesLayer() throws Exception {
        // install base feature pack
        final FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(repo);
        creator.newFeaturePack(FeaturePackLocation.fromString("org.test:base-pack:1.0.0:zip").getFPID())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("model")
                        .setName("layer1")
                        .addLayerDep("layer2", true)
                        .build())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("model")
                        .setName("layer2")
                        .build())
                .getCreator()
                .newFeaturePack(FeaturePackLocation.fromString("org.test:added-pack:1.0.0:zip").getFPID())
                        .addConfigLayer(ConfigLayerSpec.builder()
                                .setModel("model")
                                .setName("layer2")
                                .build());
        deployFeaturePacks(creator);

        // install
        getPm(installDir).provision(ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackLocation.fromString("org.test:base-pack:1.0.0"))
                .addConfig(ConfigModel.builder("model", "test.xml")
                        .includeLayer("layer1")
                        .excludeLayer("layer2")
                        .build())
                .build());
        mockInstallationData(installDir, "org.test:base-pack:1.0.0");

        getFeaturesAddAction().addFeaturePack("org.test:added-pack", Set.of("layer2"), null, "test.xml");

        final ArgumentCaptor<ProvisioningConfig> provisioningConfigArgumentCaptor = ArgumentCaptor.forClass(ProvisioningConfig.class);
        verify(prepareCandidateAction).buildCandidate(any(), any(), eq(ApplyCandidateAction.Type.FEATURE_ADD),
                provisioningConfigArgumentCaptor.capture());

        final ProvisioningConfig config = provisioningConfigArgumentCaptor.getValue();
        assertThat(config.getDefinedConfigs())
                .contains(ConfigModel.builder("model", "test.xml")
                        .includeLayer("layer1")
                        .includeLayer("layer2")
                        .build());
        assertThat(config.getDefinedConfig(new ConfigId("model", "test.xml")).getExcludedLayers())
                .isEmpty();
        assertThat(config.getFeaturePackDeps())
                .map(FeaturePackConfig::getLocation)
                .contains(
                        FeaturePackLocation.fromString("org.test:base-pack:zip"),
                        FeaturePackLocation.fromString("org.test:added-pack::zip@maven"));
    }

    @Test
    public void multipleModelsDefinedInLayersWithoutSelectedModel_throwError() throws Exception {
        // install base feature pack
        final FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(repo);
        creator.newFeaturePack(FeaturePackLocation.fromString("org.test:base-pack:1.0.0:zip").getFPID())
                .getCreator()
                .newFeaturePack(FeaturePackLocation.fromString("org.test:added-pack:1.0.0:zip").getFPID())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("model1")
                        .setName("layer1")
                        .build())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("mode2")
                        .setName("layer1")
                        .build())
        ;
        deployFeaturePacks(creator);
        // install
        installFeaturePack(installDir, "org.test:base-pack:1.0.0:zip");

        assertThatThrownBy(()->getFeaturesAddAction().addFeaturePack(
                "org.test:added-pack", Collections.emptySet(), null, null))
                .isInstanceOf(FeaturesAddAction.ModelNotDefinedException.class);
    }

    @Test
    public void selectedModelIsNotDefinedInFeaturePack_throwError() throws Exception {
        // install base feature pack
        final FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(repo);
        creator.newFeaturePack(FeaturePackLocation.fromString("org.test:base-pack:1.0.0:zip").getFPID())
                .getCreator()
                .newFeaturePack(FeaturePackLocation.fromString("org.test:added-pack:1.0.0:zip").getFPID())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("model1")
                        .setName("layer1")
                        .build());
        deployFeaturePacks(creator);
        // install
        installFeaturePack(installDir, "org.test:base-pack:1.0.0:zip");

        assertThatThrownBy(()->getFeaturesAddAction().addFeaturePack(
                "org.test:added-pack", Collections.emptySet(), "model2", null))
                .isInstanceOf(FeaturesAddAction.ModelNotDefinedException.class)
                .hasFieldOrPropertyWithValue("model", "model2");

    }

    @Test
    public void selectedModelOverridesTheDefault() throws Exception {
        // install base feature pack
        final FeaturePackCreator creator = FeaturePackCreator.getInstance().addArtifactResolver(repo);
        creator.newFeaturePack(FeaturePackLocation.fromString("org.test:base-pack:1.0.0:zip").getFPID())
                .getCreator()
                .newFeaturePack(FeaturePackLocation.fromString("org.test:added-pack:1.0.0:zip").getFPID())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("model1")
                        .setName("layer1")
                        .build())
                .addConfigLayer(ConfigLayerSpec.builder()
                        .setModel("model2")
                        .setName("layer1")
                        .build());
        deployFeaturePacks(creator);
        // install
        installFeaturePack(installDir, "org.test:base-pack:1.0.0:zip");

        getFeaturesAddAction().addFeaturePack(
                "org.test:added-pack", Collections.emptySet(), "model2", null);
        final ArgumentCaptor<ProvisioningConfig> provisioningConfigArgumentCaptor = ArgumentCaptor.forClass(ProvisioningConfig.class);
        verify(prepareCandidateAction).buildCandidate(any(), any(), eq(ApplyCandidateAction.Type.FEATURE_ADD),
                provisioningConfigArgumentCaptor.capture());

        final ProvisioningConfig config = provisioningConfigArgumentCaptor.getValue();
        assertThat(config.getDefinedConfigs())
                .contains(ConfigModel.builder("model2", "model2.xml")
                        .build());
    }

    @Test
    public void invalidFeatureNameThrowsException() throws Exception {
        mockInstallationData(installDir);
        assertThatThrownBy(()->getFeaturesAddAction().addFeaturePack(null, Collections.emptySet(), NO_MODEL, NO_CONFIG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The feature pack coordinate cannot be null");
        assertThatThrownBy(()->getFeaturesAddAction().isFeaturePackAvailable(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The feature pack coordinate cannot be null");

        assertThatThrownBy(()->getFeaturesAddAction().addFeaturePack("only_group", Collections.emptySet(), NO_MODEL, NO_CONFIG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The feature pack coordinate has to consist of <groupId>:<artifactId>");
        assertThatThrownBy(()->getFeaturesAddAction().isFeaturePackAvailable("only_group"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The feature pack coordinate has to consist of <groupId>:<artifactId>");

        assertThatThrownBy(()->getFeaturesAddAction().addFeaturePack("too:many:parts", Collections.emptySet(), NO_MODEL, NO_CONFIG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The feature pack coordinate has to consist of <groupId>:<artifactId>");
        assertThatThrownBy(()->getFeaturesAddAction().isFeaturePackAvailable("too:many:parts"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The feature pack coordinate has to consist of <groupId>:<artifactId>");
    }

    private FeaturesAddAction getFeaturesAddAction() throws MetadataException, ProvisioningException {
        final FeaturesAddAction featuresAddAction = new FeaturesAddAction(MavenOptions.OFFLINE_NO_CACHE, installDir,
                List.of(new Repository("test", repositoryUrl.toExternalForm())), null,
                candidateActionsFactory);
        return featuresAddAction;
    }

    private void deployFeaturePacks(FeaturePackCreator creator) throws ProvisioningException, IOException, DeploymentException {
        creator.install();
        deployFeaturePack("org.test", "base-pack", "1.0.0");
        deployFeaturePack("org.test", "added-pack", "1.0.0");

        final ChannelManifest manifest = MetadataTestUtils.createManifest(List.of(
                new Stream("org.test", "base-pack", "1.0.0"),
                new Stream("org.test", "added-pack", "1.0.0")
        ));
        mavenUtils.deploy(manifest, "org.test", "test", "1.0.0", repositoryUrl);
    }

    private void deployFeaturePack(String groupId, String artifactId, String version) throws IOException, DeploymentException {
        final String fileName = artifactId + "-zip-" + version + ".zip";
        Path file = repository;
        for (String part : groupId.split("\\.")) {
            file = file.resolve(part);
        }
        file = file.resolve(artifactId).resolve("zip");
        file = file.resolve(fileName);

        mavenUtils.deployFile(groupId, artifactId, version, null, "zip",
                file.toFile(),
                repositoryUrl);
    }

    private void installFeaturePack(Path path, String fpl) throws Exception {
        getPm(path).install(FeaturePackLocation.fromString(fpl));

        // mock the installation metadata
        mockInstallationData(path, fpl);
    }

    private void mockInstallationData(Path path, String... fpls) throws IOException {
        final Path metadataPath = path.resolve(METADATA_DIR);
        Files.createDirectory(metadataPath);
        MetadataTestUtils.createManifest("manifest", Collections.emptyList(), metadataPath);
        MetadataTestUtils.createChannel("channel", "org.test:test", List.of(repositoryUrl.toExternalForm()),
                metadataPath);

        final Path provisioningFile = path.resolve(Constants.PROVISIONED_STATE_DIR).resolve(Constants.PROVISIONING_XML);
        if (Files.exists(provisioningFile)) {
            java.util.stream.Stream<String> lines = FileUtils.readLines(
                    provisioningFile.toFile(),
                    StandardCharsets.UTF_8).stream();
            for (String fpl : fpls) {
                lines = lines.map(l -> l.replace(fpl, fpl.replace("1.0.0", "")));
            }
            FileUtils.writeLines(provisioningFile.toFile(),
                    lines.collect(Collectors.toList()));
        }
    }

    protected ProvisioningManager getPm(Path path) throws Exception {
        return ProvisioningManager.builder()
                .addArtifactResolver(repo)
                .setInstallationHome(path)
                .setRecordState(true)
                .build();
    }
}