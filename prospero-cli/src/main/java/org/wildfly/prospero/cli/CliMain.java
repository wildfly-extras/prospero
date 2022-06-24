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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.cli.commands.HistoryCommand;
import org.wildfly.prospero.cli.commands.InstallCommand;
import org.wildfly.prospero.cli.commands.MainCommand;
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

    private static final Logger logger = LoggerFactory.getLogger(CliMain.class);

    public static final String TARGET_PATH_ARG = "dir";
    public static final String PROVISION_CONFIG_ARG = "provision-config";

    public static void main(String[] args) {
        try {
            Console console = new CliConsole();
            ActionFactory actionFactory = new ActionFactory();
            CommandLine commandLine = createCommandLine(console, actionFactory);
            int exitCode = commandLine.execute(args);
            System.exit(exitCode);
        } catch (Exception e) {
            System.err.println("Error when processing command: " + e.getMessage());
            logger.error("Error when processing command", e);
            System.exit(ReturnCodes.PROCESSING_ERROR);
        }
    }

    public static CommandLine createCommandLine(Console console, ActionFactory actionFactory) {
        CommandLine commandLine = new CommandLine(new MainCommand(console));
        commandLine.addSubcommand(new InstallCommand(console, actionFactory));
        commandLine.addSubcommand(new UpdateCommand(console, actionFactory));
        commandLine.addSubcommand(new HistoryCommand(console, actionFactory));
        commandLine.addSubcommand(new RevertCommand(console, actionFactory));
        commandLine.setHelpFactory(new CustomHelp.CustomHelpFactory());
        return commandLine;
    }

}
