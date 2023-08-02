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

package org.wildfly.prospero.cli;

import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom handler for argument parsing.
 * Treats unmatched positional parameters as unknown commands.
 */
class UnknownCommandParameterExceptionHandler implements CommandLine.IParameterExceptionHandler {
    protected static final String COMMAND_SEPARATOR = " ";
    private final CommandLine.IParameterExceptionHandler delegate;
    private final PrintStream writer;

    public UnknownCommandParameterExceptionHandler(CommandLine.IParameterExceptionHandler delegate, PrintStream writer) {
        this.delegate = delegate;
        this.writer = writer;
    }

    @Override
    public int handleParseException(CommandLine.ParameterException ex, String[] args) throws Exception {
        if (ex instanceof CommandLine.UnmatchedArgumentException) {
            final CommandLine.UnmatchedArgumentException argEx = (CommandLine.UnmatchedArgumentException) ex;

            if (argEx.isUnknownOption()) {
                return delegate.handleParseException(ex, args);
            }

            if (!currentCommandHasSubcommands(argEx)) {
                return delegate.handleParseException(ex, args);
            }

            final CommandLine.Help.ColorScheme colorScheme = CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.AUTO);
            final String commandName = argEx.getCommandLine().getCommandSpec().name();
            final String fullCommand = commandName + COMMAND_SEPARATOR + StringUtils.join(argEx.getUnmatched(), COMMAND_SEPARATOR);
            writer.println(colorScheme.errorText(CliMessages.MESSAGES.unknownCommand(fullCommand)));

            final List<String> suggestions = argEx.getSuggestions();
            if (suggestions.isEmpty()) {
                argEx.getCommandLine().usage(writer);
            } else {
                writer.printf(CliMessages.MESSAGES.commandSuggestions(prefix(commandName, suggestions)));
            }
            return ReturnCodes.INVALID_ARGUMENTS;
        }
        return delegate.handleParseException(ex, args);
    }

    private static boolean currentCommandHasSubcommands(CommandLine.UnmatchedArgumentException argEx) {
        return !argEx.getCommandLine().getParseResult().commandSpec().subcommands().isEmpty();
    }

    private List<String> prefix(String commandName, List<String> commandSuggestions) {
        return commandSuggestions.stream().map(s -> commandName + COMMAND_SEPARATOR + s).collect(Collectors.toList());
    }
}
