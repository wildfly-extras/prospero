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

package org.wildfly.prospero.cli;

import org.jboss.logging.Logger;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.cli.commands.ChannelCommand;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.cli.commands.CloneCommand;
import org.wildfly.prospero.cli.commands.CompletionCommand;
import org.wildfly.prospero.cli.commands.HistoryCommand;
import org.wildfly.prospero.cli.commands.InstallCommand;
import org.wildfly.prospero.cli.commands.MainCommand;
import org.wildfly.prospero.cli.commands.RevertCommand;
import org.wildfly.prospero.cli.commands.UpdateCommand;
import org.wildfly.prospero.cli.commands.channel.ChannelAddCommand;
import org.wildfly.prospero.cli.commands.channel.ChannelInitializeCommand;
import org.wildfly.prospero.cli.commands.channel.ChannelPromoteCommand;
import org.wildfly.prospero.cli.commands.channel.ChannelRemoveCommand;
import picocli.CommandLine;

public class CliMain {

    static {
        enableJBossLogManager();
    }

    private static void enableJBossLogManager() {
        if (System.getProperty("java.util.logging.manager") == null) {
            System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        }
    }

    private static final Logger logger = Logger.getLogger(CliMain.class);

    public static void main(String[] args) {
        try {
            Console console = new CliConsole();
            CommandLine commandLine = createCommandLine(console);
            int exitCode = commandLine.execute(args);
            System.exit(exitCode);
        } catch (Exception e) {
            System.err.println(CliMessages.MESSAGES.errorWhenProcessingCommand() + e.getMessage());
            logger.error(CliMessages.MESSAGES.errorWhenProcessingCommand(), e);
            System.exit(ReturnCodes.PROCESSING_ERROR);
        }
    }

    public static CommandLine createCommandLine(Console console) {
        return createCommandLine(console, new ActionFactory());
    }

    public static CommandLine createCommandLine(Console console, ActionFactory actionFactory) {
        CommandLine commandLine = new CommandLine(new MainCommand(console));

        commandLine.addSubcommand(new InstallCommand(console, actionFactory));
        commandLine.addSubcommand(new UpdateCommand(console, actionFactory));
        commandLine.addSubcommand(new HistoryCommand(console, actionFactory));
        commandLine.addSubcommand(new RevertCommand(console, actionFactory));
        commandLine.addSubcommand(new ChannelCommand(console, actionFactory));
        commandLine.addSubcommand(new CompletionCommand());

        CommandLine channelCmd = commandLine.getSubcommands().get(CliConstants.Commands.CHANNEL);
        channelCmd.addSubcommand(new ChannelAddCommand(console, actionFactory));
        channelCmd.addSubcommand(new ChannelRemoveCommand(console, actionFactory));
        channelCmd.addSubcommand(new ChannelCommand.ChannelListCommand(console, actionFactory));
        channelCmd.addSubcommand(new ChannelInitializeCommand(console, actionFactory));
        channelCmd.addSubcommand(new ChannelPromoteCommand(console, actionFactory));

        CloneCommand cloneCommand = new CloneCommand(console, actionFactory);
        commandLine.addSubcommand(cloneCommand);
        cloneCommand.addSubCommands(commandLine);

        commandLine.setUsageHelpAutoWidth(true);
        commandLine.setExecutionExceptionHandler(new ExecutionExceptionHandler(console));

        return commandLine;
    }

}
