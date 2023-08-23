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
import java.util.Set;

import static org.wildfly.prospero.cli.commands.CliConstants.Commands.FEATURE_PACKS_ALIAS;

@CommandLine.Command(name = CliConstants.Commands.FEATURE_PACKS, aliases = {FEATURE_PACKS_ALIAS})
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
                featuresAddAction.addFeaturePack(fpl, layers == null ? Collections.emptySet() : layers, parseConfigName());
            } catch (FeaturesAddAction.LayerNotFoundException e) {
                if (!e.getSupportedLayers().isEmpty()) {
                    console.error(CliMessages.MESSAGES.layerNotSupported(fpl, e.getLayers(), e.getSupportedLayers()));
                } else {
                    console.error(CliMessages.MESSAGES.layerNotSupported(fpl));
                }
                return ReturnCodes.INVALID_ARGUMENTS;
            } catch (FeaturesAddAction.ModelNotDefinedException e) {
                console.error(CliMessages.MESSAGES.modelNotSupported(fpl, e.getModel(), e.getSupportedModels()));
                return ReturnCodes.INVALID_ARGUMENTS;
            } catch (FeaturesAddAction.ConfigurationNotFoundException e) {
                console.error(CliMessages.MESSAGES.galleonConfigNotSupported(fpl, e.getModel(), e.getName()));
                return ReturnCodes.INVALID_ARGUMENTS;
            }

            final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
            console.println(CliMessages.MESSAGES.operationCompleted(totalTime));

            return ReturnCodes.SUCCESS;
        }

        private ConfigId parseConfigName() {
            if (config == null) {
                return null;
            }
            int i = config.indexOf("/");
            if (i < 0) {
                return new ConfigId(null, config.trim());
            }

            if (i == config.length() -1) {
                return new ConfigId(config.substring(0, i).trim(), null);
            } else {
                return new ConfigId(config.substring(0, i).trim(), config.substring(i+1).trim());
            }
        }
    }


    public FeaturesCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory, CliConstants.Commands.FEATURE_PACKS, List.of(new AddCommand(console, actionFactory)));
    }
}
