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

import org.wildfly.prospero.actions.FeaturesAddAction;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

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
            // TODO: add all the validation, etc

            final MavenSessionManager msm = new MavenSessionManager();
            final FeaturesAddAction featuresAddAction = new FeaturesAddAction(msm, directory.get(), console);
            featuresAddAction.addFeaturePack(fpl, layers==null? Collections.emptySet():layers, model, config);

            return ReturnCodes.SUCCESS;
        }
    }


    public FeaturesCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory, "features", List.of(new AddCommand(console, actionFactory)));
    }
}
