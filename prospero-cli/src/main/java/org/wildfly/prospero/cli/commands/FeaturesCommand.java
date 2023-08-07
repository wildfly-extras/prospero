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

import org.apache.commons.lang3.tuple.Pair;
import org.jboss.galleon.config.FeaturePackConfig;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.actions.FeaturesAddAction;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.RepositoryDefinition;
import org.wildfly.prospero.cli.ReturnCodes;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CommandLine.Command(name = CliConstants.Commands.FEATURES)
public class FeaturesCommand extends AbstractParentCommand {

    @CommandLine.Command(name = CliConstants.Commands.ADD, sortOptions = false)
    public static class AddCommand extends AbstractMavenCommand {

        @CommandLine.Option(
                names = CliConstants.FPL,
                paramLabel = CliConstants.FEATURE_PACK_REFERENCE,
                required = true)
        private String fpl;

        @CommandLine.Option(names = CliConstants.LAYERS, split = ",")
        private Set<String> layers;

        @CommandLine.Option(names = CliConstants.MODEL)
        private String model;

        @CommandLine.Option(names = CliConstants.CONFIG)
        private String config;

        @CommandLine.Option(names = {CliConstants.Y, CliConstants.YES})
        boolean skipConfirmation;

        public AddCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            final long startTime = System.currentTimeMillis();

            if (fpl.split(":").length != 2) {
                throw CliMessages.MESSAGES.featurePackNameNotMavenCoordinate();
            }

            final Path installationDir = determineInstallationDirectory(directory);

            final MavenOptions mavenOptions = parseMavenOptions();

            final List<Repository> repositories = RepositoryDefinition.from(temporaryRepositories);

            console.println(CliMessages.MESSAGES.featuresAddHeader(fpl, installationDir));

            final FeaturesAddAction featuresAddAction = actionFactory.featuresAddAction(installationDir, mavenOptions, repositories, console);

            if (!featuresAddAction.isFeaturePackAvailable(fpl)) {
                console.error(CliMessages.MESSAGES.featurePackNotFound(fpl));
                return ReturnCodes.INVALID_ARGUMENTS;
            }

            if (!skipConfirmation) {
                console.confirm(CliMessages.MESSAGES.featuresAddPrompt(),
                        CliMessages.MESSAGES.featuresAddPromptAccepted(),
                        CliMessages.MESSAGES.featuresAddPromptCancelled());
            } else {
                console.println(CliMessages.MESSAGES.featuresAddPromptAccepted());
            }

            try {
                featuresAddAction.addFeaturePack(fpl, layers == null ? Collections.emptySet() : layers, model, config);
            } catch (FeaturesAddAction.LayerNotFoundException e) {
                if (!e.getSupportedLayers().isEmpty()) {
                    console.error(CliMessages.MESSAGES.layerNotSupported(fpl, e.getLayer(), e.getSupportedLayers()));
                } else {
                    console.error(CliMessages.MESSAGES.layerNotSupported(fpl));
                }
                return ReturnCodes.INVALID_ARGUMENTS;
            } catch (FeaturesAddAction.ModelNotDefinedException e) {
                console.error(CliMessages.MESSAGES.modelNotSupported(fpl, e.getModel(), e.getSupportedModels()));
                return ReturnCodes.INVALID_ARGUMENTS;
            }

            final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
            console.println(CliMessages.MESSAGES.operationCompleted(totalTime));

            return ReturnCodes.SUCCESS;
        }
    }

    @CommandLine.Command(name = CliConstants.Commands.LIST, sortOptions = false)
    public static class ListCommand extends AbstractMavenCommand {

        public ListCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            final Path installationDir = determineInstallationDirectory(directory);
            final MavenOptions mavenOptions = parseMavenOptions();

            FeaturesAddAction actions = actionFactory.featuresAddAction(installationDir, mavenOptions, Collections.emptyList(), console);
            Map<FeaturePackConfig, List<Pair<String, String>>> featurePackMap = actions.getInstalledFeaturePacks();


            if (!featurePackMap.isEmpty()) {
                console.println(CliMessages.MESSAGES.featuresListHeader(installationDir.toAbsolutePath()));
                console.println("");
            }

            for (Map.Entry<FeaturePackConfig, List<Pair<String, String>>> entry: featurePackMap.entrySet()) {
                FeaturePackConfig featurePackConfig = entry.getKey();
                List<Pair<String, String>> layers = entry.getValue();
                String featurePackGA = featurePackConfig.getLocation().getProducerName();
                if (featurePackGA.endsWith("::zip")) {
                    featurePackGA = featurePackGA.substring(0, featurePackGA.length() - 5);
                }
                console.println("  " + featurePackGA);

                if (!layers.isEmpty()) {
                    console.println("    " + CliMessages.MESSAGES.featuresIncludedLayers());
                    for (Pair<String, String> layer: layers) {
                        console.println(String.format("      %s=%s, %s=%s",
                                CliMessages.MESSAGES.featuresModel(), CliMessages.MESSAGES.featuresLayer(),
                                layer.getLeft(), layer.getRight()));
                    }
                }
            }

            return ReturnCodes.SUCCESS;
        }
    }


    public FeaturesCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory, "features", List.of(
                new AddCommand(console, actionFactory),
                new ListCommand(console, actionFactory)));
    }
}
