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

@CommandLine.Command(name = "features")
public class FeaturesCommand extends AbstractParentCommand {

    @CommandLine.Command(name = "add", sortOptions = false)
    public static class AddCommand extends AbstractMavenCommand {

        @CommandLine.Option(
                names = CliConstants.FPL,
                required = true)
        private String fpl;

        @CommandLine.Option(names = "--layers", split = ",")
        private Set<String> layers;

        @CommandLine.Option(names = "--model")
        private String model;

        @CommandLine.Option(names = "--config")
        private String config;

        public AddCommand(CliConsole console, ActionFactory actionFactory) {
            super(console, actionFactory);
        }

        @Override
        public Integer call() throws Exception {
            final long startTime = System.currentTimeMillis();
            // TODO: add all the validation, etc

            final Path installationDir = determineInstallationDirectory(directory);

            final MavenOptions mavenOptions = parseMavenOptions();

            final List<Repository> repositories = RepositoryDefinition.from(temporaryRepositories);

            final FeaturesAddAction featuresAddAction = new FeaturesAddAction(mavenOptions, installationDir, repositories, console);
            featuresAddAction.addFeaturePack(fpl, layers==null? Collections.emptySet():layers, model, config);

            final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
            console.println(CliMessages.MESSAGES.operationCompleted(totalTime));

            return ReturnCodes.SUCCESS;
        }
    }


    public FeaturesCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory, "features", List.of(new AddCommand(console, actionFactory)));
    }
}
