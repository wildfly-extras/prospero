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

import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ReturnCodes;
import picocli.CommandLine;

import java.util.List;

public abstract class AbstractParentCommand extends AbstractCommand {

    private final String name;
    private final List<AbstractCommand> subcommands;
    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    protected AbstractParentCommand(Console console, ActionFactory actionFactory, String name, List<AbstractCommand> subcommands) {
        super(console, actionFactory);
        this.name = name;
        this.subcommands = subcommands;
    }

    public void addSubCommands(CommandLine rootCmd) {
        CommandLine cmd = rootCmd.getSubcommands().get(name);
        for (AbstractCommand subcommand : subcommands) {
            cmd.addSubcommand(subcommand);
        }
    }

    @Override
    public Integer call() throws Exception {
        spec.commandLine().usage(console.getErrOut());
        return ReturnCodes.INVALID_ARGUMENTS;
    }

}
