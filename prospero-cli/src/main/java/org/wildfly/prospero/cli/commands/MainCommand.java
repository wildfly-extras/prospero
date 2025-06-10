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

package org.wildfly.prospero.cli.commands;

import java.io.IOException;
import java.util.List;
import java.util.ResourceBundle;

import org.wildfly.prospero.DistributionInfo;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.ReturnCodes;
import picocli.CommandLine;

@CommandLine.Command(name = "${prospero.dist.name}", resourceBundle = "UsageMessages",
        versionProvider = MainCommand.VersionProvider.class)
public class MainCommand extends AbstractParentCommand {

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    @SuppressWarnings("unused")
    @CommandLine.Option(names = {CliConstants.V, CliConstants.VERSION}, versionHelp = true)
    boolean version;

    public MainCommand(CliConsole console, ActionFactory actionFactory) {

        super(console, actionFactory, "prospero",
                List.of(
                        new InstallCommand(console, actionFactory),
                        new UpdateCommand(console, actionFactory),
                        new PrintLicensesCommand(console, actionFactory),
                        new HistoryCommand(console, actionFactory),
                        new RevertCommand(console, actionFactory),
                        new ChannelCommand(console, actionFactory),
                        new CompletionCommand(console, actionFactory),
                        new CloneCommand(console, actionFactory),
                        new FeaturesCommand(console, actionFactory)
                )
        );
    }

    @Override
    public Integer call() throws IOException {
        // print welcome message - this is not printed when -h option is set
        ResourceBundle usageBundle = ResourceBundle.getBundle("UsageMessages");

        console.println(CommandLine.Help.Ansi.AUTO.string(usageBundle.getString("prospero.welcomeMessage")));

        // print main command usage
        spec.commandLine().usage(console.getStdOut());
        return ReturnCodes.SUCCESS;
    }

    static class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            return new String[] {DistributionInfo.getVersion()};
        }
    }
}
