/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.cli;

import org.jboss.logging.Logger;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.cli.commands.ApplyPatchCommand;
import org.wildfly.prospero.cli.commands.HistoryCommand;
import org.wildfly.prospero.cli.commands.InstallCommand;
import org.wildfly.prospero.cli.commands.MainCommand;
import org.wildfly.prospero.cli.commands.RepositoryAddCommand;
import org.wildfly.prospero.cli.commands.RepositoryCommand;
import org.wildfly.prospero.cli.commands.RepositoryListCommand;
import org.wildfly.prospero.cli.commands.RepositoryRemoveCommand;
import org.wildfly.prospero.cli.commands.RevertCommand;
import org.wildfly.prospero.cli.commands.UpdateCommand;
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
        commandLine.addSubcommand(new ApplyPatchCommand(console, actionFactory));
        commandLine.addSubcommand(new HistoryCommand(console, actionFactory));
        commandLine.addSubcommand(new RevertCommand(console, actionFactory));
        commandLine.addSubcommand(new RepositoryCommand(console, actionFactory));

        CommandLine repoCmd = commandLine.getSubcommands().get(CliConstants.REPOSITORY);
        repoCmd.addSubcommand(new RepositoryAddCommand(console, actionFactory));
        repoCmd.addSubcommand(new RepositoryRemoveCommand(console, actionFactory));
        repoCmd.addSubcommand(new RepositoryListCommand(console, actionFactory));

        commandLine.setUsageHelpAutoWidth(true);
        commandLine.setExecutionExceptionHandler(new ExecutionExceptionHandler(console));

        return commandLine;
    }

}
