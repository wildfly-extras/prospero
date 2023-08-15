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

package org.wildfly.prospero.cli.commands;

import org.jboss.galleon.config.ConfigId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.prospero.actions.FeaturesAddAction;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.wildfly.common.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class FeaturesCommandTest extends AbstractMavenCommandTest {
    public static final String A_PROSPERO_FP = UpdateCommand.PROSPERO_FP_GA + ":1.0.0";
    @Mock
    private ActionFactory actionFactory;
    @Mock
    private FeaturesAddAction featuresAddAction;

    @Captor
    private ArgumentCaptor<MavenOptions> mavenOptions;

    @Captor
    private ArgumentCaptor<ConfigId> configNameCaptor;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path installationDir;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        installationDir = tempFolder.newFolder().toPath();

        when(actionFactory.featuresAddAction(any(), any(), any(), any())).thenReturn(featuresAddAction);
        MetadataTestUtils.createInstallationMetadata(installationDir);
        MetadataTestUtils.createGalleonProvisionedState(installationDir, A_PROSPERO_FP);

        when(featuresAddAction.isFeaturePackAvailable(any()))
                .thenReturn(true);
    }

    @Test
    public void currentDirNotValidInstallation() {
        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD, CliConstants.FPL, "test:test");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(CliMessages.MESSAGES.invalidInstallationDir(UpdateCommand.currentDir().toAbsolutePath())
                .getMessage()));
    }

    @Test
    public void errorIfFplIsNotPresent() {
        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD, CliConstants.DIR, "test");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertThat(getErrorOutput())
                .contains(String.format("Missing required option: '%s=%s'", CliConstants.FPL, CliConstants.FEATURE_PACK_REFERENCE));
    }

    @Test
    public void passArgumentsToFeaturesAddAction_OnlyFpl() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.FPL, "test:test");
        assertEquals(ReturnCodes.SUCCESS, exitCode);

        final ArgumentCaptor<String> fplCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Set<String>> layersCaptor = ArgumentCaptor.forClass(Set.class);
        verify(featuresAddAction).addFeaturePack(fplCaptor.capture(), layersCaptor.capture(),
                configNameCaptor.capture());

        assertEquals(fplCaptor.getValue(), "test:test");
        assertEquals(Collections.emptySet(), layersCaptor.getValue());
        assertEquals(configNameCaptor.getValue(), null);
    }

    @Test
    public void passArgumentsToFeaturesAddAction_LayersAreSplit() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.FPL, "test:test",
                CliConstants.LAYERS, "layer1,layer2");
        assertEquals(ReturnCodes.SUCCESS, exitCode);

        final ArgumentCaptor<Set<String>> layersCaptor = ArgumentCaptor.forClass(Set.class);
        verify(featuresAddAction).addFeaturePack(any(), layersCaptor.capture(),
                any());

        assertEquals(Set.of("layer1", "layer2"), layersCaptor.getValue());
    }

    @Test
    public void passArgumentsToFeaturesAddAction_ModuleOnlyConfig() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.FPL, "test:test",
                CliConstants.CONFIG, "test/");
        assertEquals(ReturnCodes.SUCCESS, exitCode);

        verify(featuresAddAction).addFeaturePack(any(), any(),
                configNameCaptor.capture());

        assertEquals(new ConfigId("test", null), configNameCaptor.getValue());
    }

    @Test
    public void passArgumentsToFeaturesAddAction_NameOnlyConfig() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.FPL, "test:test",
                CliConstants.CONFIG, "test");
        assertEquals(ReturnCodes.SUCCESS, exitCode);

        verify(featuresAddAction).addFeaturePack(any(), any(),
                configNameCaptor.capture());

        assertEquals(new ConfigId(null, "test"), configNameCaptor.getValue());
    }

    @Test
    public void passArgumentsToFeaturesAddAction_AllParameters() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.FPL, "test:test",
                CliConstants.LAYERS, "layer1",
                CliConstants.CONFIG, "model/config");
        assertEquals(ReturnCodes.SUCCESS, exitCode);

        final ArgumentCaptor<Set<String>> layersCaptor = ArgumentCaptor.forClass(Set.class);
        verify(featuresAddAction).addFeaturePack(any(), layersCaptor.capture(), configNameCaptor.capture());

        assertEquals(Set.of("layer1"), layersCaptor.getValue());
        assertEquals(new ConfigId("model", "config"), configNameCaptor.getValue());
    }

    @Test
    public void nonExistingFeaturePack_CausesError() throws Exception {
        when(featuresAddAction.isFeaturePackAvailable("test:idontexist"))
                .thenReturn(false);

        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.FPL, "test:idontexist",
                CliConstants.LAYERS, "layer1",
                CliConstants.CONFIG, "model/config");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertThat(getErrorOutput())
                .contains(CliMessages.MESSAGES.featurePackNotFound("test:idontexist"));
    }

    @Test
    public void unableToResolveFeaturePack_CausesError() throws Exception {
        when(featuresAddAction.isFeaturePackAvailable("test:idontexist"))
                .thenThrow(new ArtifactResolutionException("Test error", null,
                        Set.of(new ArtifactCoordinate("test", "idontexist", "zip", null, "1.0.0")),
                        Set.of(), false));

        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.FPL, "test:idontexist",
                CliConstants.LAYERS, "layer1",
                CliConstants.CONFIG, "model/config");
        assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);
        assertThat(getErrorOutput())
                .contains("Unable to resolve artifacts:")
                .contains("test:idontexist:zip:1.0.0 [missing]");
    }

    @Test
    public void fplWithOnePartFails() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.FPL, "test");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertThat(getErrorOutput())
                .contains(CliMessages.MESSAGES.featurePackNameNotMavenCoordinate().getMessage());
    }

    @Test
    public void fplWithTreePartsFails() throws Exception {
        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.FPL, "org.test:test:1.2.3");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertThat(getErrorOutput())
                .contains(CliMessages.MESSAGES.featurePackNameNotMavenCoordinate().getMessage());
    }

    @Test
    public void nonExistingLayersShowsError() throws Exception {
        doThrow(new FeaturesAddAction.LayerNotFoundException("foo", Set.of("idontexist"), Set.of("layer1", "layer2")))
                .when(featuresAddAction).addFeaturePack("org.test:test", Set.of("idontexist"), null);
        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.LAYERS, "idontexist",
                CliConstants.FPL, "org.test:test");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertThat(getErrorOutput())
                .contains("The feature pack `org.test:test` does not provide requested layers [idontexist].")
                .contains("Supported layers are [layer1, layer2]");
    }

    @Test
    public void nonExistingLayersShowsErrorNoAvailableLayers() throws Exception {
        doThrow(new FeaturesAddAction.LayerNotFoundException("foo", Set.of("idontexist"), Collections.emptySet()))
                .when(featuresAddAction).addFeaturePack("org.test:test", Set.of("idontexist"), null);
        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.LAYERS, "idontexist",
                CliConstants.FPL, "org.test:test");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertThat(getErrorOutput())
                .contains("The feature pack `org.test:test` does not provide any layers.")
                .contains("Try removing the --layers parameter");
    }


    @Test
    public void nonExistingModelShowsError() throws Exception {
        doThrow(new FeaturesAddAction.ModelNotDefinedException("test", "idontexist", Set.of("model1", "model2")))
                .when(featuresAddAction).addFeaturePack("org.test:test", Collections.emptySet(),
                        new ConfigId("idontexist", "test"));
        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.CONFIG, "idontexist/test",
                CliConstants.FPL, "org.test:test");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertThat(getErrorOutput())
                .contains("The feature pack `org.test:test` does not provide requested model `idontexist`.")
                .contains("Supported models are [model1, model2]");
    }

    @Test
    public void nonExistingConfigurationShowsError() throws Exception {
        doThrow(new FeaturesAddAction.ConfigurationNotFoundException("test", new ConfigId("test", "idontexist")))
                .when(featuresAddAction).addFeaturePack("org.test:test", Collections.emptySet(),
                        new ConfigId("test", "idontexist"));
        int exitCode = commandLine.execute(CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD,
                CliConstants.DIR, installationDir.toString(),
                CliConstants.CONFIG, "test/idontexist",
                CliConstants.FPL, "org.test:test");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertThat(getErrorOutput())
                .contains("The feature pack `org.test:test` does not provide requested configuration `test/idontexist`.");
    }

    @Override
    protected ActionFactory createActionFactory() {
        return actionFactory;
    }

    @Override
    protected MavenOptions getCapturedMavenOptions() throws Exception {
        Mockito.verify(actionFactory).featuresAddAction(any(), mavenOptions.capture(), any(), any());
        return mavenOptions.getValue();
    }

    @Override
    protected String[] getDefaultArguments() {
        return new String[] {CliConstants.Commands.FEATURE_PACKS, CliConstants.Commands.ADD, CliConstants.DIR, installationDir.toString(), CliConstants.FPL, "foo:bar"};
    }
}