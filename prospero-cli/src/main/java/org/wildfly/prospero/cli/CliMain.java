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
import org.jboss.logmanager.Configurator;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.PropertyConfigurator;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.wildfly.prospero.DistributionInfo;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.Stability;
import org.wildfly.prospero.VersionLogger;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.cli.commands.MainCommand;
import picocli.CommandLine;

import java.util.Arrays;

public class CliMain {

    static {
        enableJBossLogManager();
    }

    private static void enableJBossLogManager() {
        if (System.getProperty("java.util.logging.manager") == null) {
            System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
        }
    }

    static final Logger logger = Logger.getLogger(CliMain.class);

    public static void main(String[] args) {
        final boolean isDebug = Arrays.stream(args).anyMatch(CliConstants.DEBUG::equals);
        if (isDebug) {
            enableDebugLogging();
        }
        VersionLogger.logVersionOnStartup();

        try {
            int exitCode = execute(args);
            System.exit(exitCode);
        } catch (Exception e) {
            logException(e);
            System.exit(ReturnCodes.PROCESSING_ERROR);
        }
    }

    public static CommandLine createCommandLine(CliConsole console, String[] args) {
        return createCommandLine(console, args, new ActionFactory());
    }

    public static CommandLine createCommandLine(CliConsole console, String[] args, ActionFactory actionFactory) {
        final Stability overrideStability = parseStability(args);

        if (overrideStability != null) {
            DistributionInfo.setStability(overrideStability);
        }

        final CommandLine commandLine = new StabilityAwareCommandBuilder().build(new MainCommand(console, actionFactory));

        commandLine.setUsageHelpAutoWidth(true);
        final boolean isVerbose = Arrays.stream(args).anyMatch(s -> s.equals(CliConstants.VV) || s.equals(CliConstants.VERBOSE));
        final CommandLine.IParameterExceptionHandler rootParameterExceptionHandler = commandLine.getParameterExceptionHandler();
        commandLine.setExecutionExceptionHandler(new ExecutionExceptionHandler(console, isVerbose));

        commandLine.setParameterExceptionHandler(new UnknownCommandParameterExceptionHandler(rootParameterExceptionHandler, System.err, isVerbose));

        return commandLine;
    }

    private static Stability parseStability(String[] args) {
        // we need to manually parse stability argument
        //   it can either be one or two arguments - --stability=Preview OR --stability Preview
        //   we need to handle a case where the value is not provided - e.g. --stability -vv
        Stability overrideStability = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--stability=")) {
                final String[] arg = args[i].split("=");
                if (arg.length == 1) {
                    throw new IllegalArgumentException("Missing value for argument --stability");
                }
                final String value = arg[1].trim();
                overrideStability = Stability.from(value);
            } else if (args[i].equals("--stability")) {
                if (args.length -1  <= i+1) {
                    overrideStability = Stability.from(args[i + 1]);
                } else {
                    throw new IllegalArgumentException("Missing value for argument --stability");
                }
            }
        }

        if (overrideStability != null) {
            ProsperoLogger.ROOT_LOGGER.debug("Switching to stability level: " + overrideStability);
        }
        return overrideStability;
    }

    private static void enableDebugLogging() {
        Configurator c = org.jboss.logmanager.Logger.getLogger("").getAttachment(Configurator.ATTACHMENT_KEY);
        if (c instanceof PropertyConfigurator) {
            LogContextConfiguration lcc = ((PropertyConfigurator) c).getLogContextConfiguration();
            lcc.getLoggerConfiguration("org.wildfly.prospero").setLevel(Level.DEBUG.getName());
            lcc.getHandlerConfiguration("CONSOLE").setLevel(Level.DEBUG.getName());
            if (!lcc.getLoggerConfiguration("").getHandlerNames().contains("CONSOLE")) {
                lcc.getLoggerConfiguration("").addHandlerName("CONSOLE");
            }
            lcc.commit();
        } else {
            logger.warn("Cannot change logging level, using default.");
        }
    }

    static int execute(String[] args) {
        CliConsole console = new CliConsole();
        CommandLine commandLine = createCommandLine(console, args);
        return commandLine.execute(args);
    }

    static void logException(Exception e) {
        System.err.println(CliMessages.MESSAGES.errorWhenProcessingCommand() + e.getMessage());
        logger.error(CliMessages.MESSAGES.errorWhenProcessingCommand(), e);
    }

}
