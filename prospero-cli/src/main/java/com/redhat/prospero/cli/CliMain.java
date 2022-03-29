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

package com.redhat.prospero.cli;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.redhat.prospero.actions.Console;
import com.redhat.prospero.actions.Installation;
import com.redhat.prospero.actions.InstallationHistory;
import com.redhat.prospero.actions.Update;
import com.redhat.prospero.api.ProvisioningRuntimeException;
import com.redhat.prospero.api.exceptions.OperationException;
import com.redhat.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    public static final String FPL_ARG = "fpl";
    public static final String CHANNEL_FILE_ARG = "channel-file";
    public static final String CHANNEL_REPO = "channel-repo";
    public static final String CHANNEL = "channel";
    public static final String DRY_RUN = "dry-run";
    public static final String LOCAL_REPO = "local-repo";
    public static final String OFFLINE = "offline";
    public static final String REVISION = "revision";

    private final ActionFactory actionFactory;
    private final Console console = new CliConsole();
    private final Map<String, Command> operations;

    public CliMain(ActionFactory actionFactory) {
        this.actionFactory = actionFactory;
        this.operations = loadCommands();
    }

    public static void main(String[] args) {
        try {
            new CliMain(new ActionFactory()).handleArgs(args);
        } catch (ArgumentParsingException e) {
            System.err.println(e.getMessage());
            logger.error("Argument parsing error", e);
            System.exit(1);
        } catch (OperationException e) {
            System.err.println(e.getMessage());
            logger.error("Operation error", e);
            System.exit(1);
        } catch (ProvisioningRuntimeException e) {
            System.err.println(e.getMessage());
            logger.error("Runtime error", e);
            System.exit(1);
        }
    }

    public void handleArgs(String[] args) throws ArgumentParsingException, OperationException {
        final String operation = args[0];
        if (!operations.containsKey(operation)) {
            throw new ArgumentParsingException("Unknown operation " + operation);
        }

        final Command command = operations.get(operation);

        Map<String, String> parsedArgs = parseArguments(args, command);

        command.execute(parsedArgs);
    }

    private Map<String, Command> loadCommands() {
        Map<String, Command> operations = new HashMap<>();
        Command command = new HistoryCommand(actionFactory, console);
        operations.put(command.getOperationName(), command);
        command = new RevertCommand(actionFactory);
        operations.put(command.getOperationName(), command);
        command = new InstallCommand(actionFactory);
        operations.put(command.getOperationName(), command);
        command = new UpdateCommand(actionFactory);
        operations.put(command.getOperationName(), command);
        return operations;
    }

    private Map<String, String> parseArguments(String[] args, Command command) throws ArgumentParsingException {
        Map<String, String> parsedArgs = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            final int nameEndIndex = args[i].indexOf('=');

            if (nameEndIndex < 0) {
                throw new ArgumentParsingException("Argument value cannot be empty");
            }

            if (!args[i].startsWith("--")) {
                throw new ArgumentParsingException("Argument [%s] not recognized", args[i]);
            }

            final String name = (nameEndIndex > 0) ? args[i].substring(2, nameEndIndex) : args[i];
            final String value = args[i].substring(nameEndIndex + 1);

            if (!command.getSupportedArguments().contains(name)) {
                throw new ArgumentParsingException("Argument name [--%s] not recognized", name);
            }

            if (value.isEmpty()) {
                throw new ArgumentParsingException("Argument value cannot be empty");
            }

            parsedArgs.put(name, value);
        }
        return parsedArgs;
    }

    static class ActionFactory {
        public Installation install(Path targetPath, MavenSessionManager mavenSessionManager) {
            return new Installation(targetPath, mavenSessionManager, new CliConsole());
        }

        public Update update(Path targetPath, MavenSessionManager mavenSessionManager) throws OperationException, ProvisioningException {
            return new Update(targetPath, mavenSessionManager, new CliConsole());
        }

        public InstallationHistory history(Path targetPath) {
            return new InstallationHistory(targetPath, new CliConsole());
        }
    }

}
