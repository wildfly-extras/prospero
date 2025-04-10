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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class UnknownCommandParameterExceptionHandlerTest {

    @Mock
    private CommandLine.IParameterExceptionHandler delegate;
    private ByteArrayOutputStream out;
    private UnknownCommandParameterExceptionHandler handler;

    @Before
    public void setUp() {
        out = new ByteArrayOutputStream();
        handler = new UnknownCommandParameterExceptionHandler(delegate, new PrintStream(out), false);
    }

    @Test
    public void nonParameterExceptionIsHandledByDelegate() throws Exception {
        final CommandLine.ParameterException ex = mock(CommandLine.ParameterException.class);
        final String[] args = {};
        handler.handleParseException(ex, args);

        verify(delegate).handleParseException(ex, args);
    }

    @Test
    public void unmatchedOptionIsPassedToDelegate() throws Exception {
        final CommandLine.UnmatchedArgumentException ex = new CommandLine.UnmatchedArgumentException(new CommandLine(new TestCommand()), List.of("--boo"));
        final String[] args = {"--boo"};
        handler.handleParseException(ex, args);

        verify(delegate).handleParseException(ex, args);
    }

    @Test
    public void unmatchedArgumentShowsSuggestion_OneMatched() throws Exception {
        final CommandLine commandLine = new CommandLine(new RootCommand());
        commandLine.addSubcommand(new TestCommand());
        commandLine.getSubcommands().get("test").addSubcommand(new TestSubCommand());

        final CommandLine.UnmatchedArgumentException ex = getException(commandLine, "test", "sup");

        handler.handleParseException(ex, new String[]{"test", "sup"});

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("Unknown command `test sup`")
                .contains("Did you mean: test sub?");
    }

    @Test
    public void unmatchedArgumentShowsSuggestion_MultipleMatched() throws Exception {
        final CommandLine commandLine = new CommandLine(new RootCommand());
        commandLine.addSubcommand(new TestCommand());
        commandLine.getSubcommands().get("test").addSubcommand(new TestSubCommand());
        commandLine.getSubcommands().get("test").addSubcommand(new TestSubCommandTwo());


        final CommandLine.UnmatchedArgumentException ex = getException(commandLine, "test", "sup");
        handler.handleParseException(ex, new String[]{"test sup"});

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("Unknown command `test sup`")
                .contains("Did you mean one of: test sub or test sub2?");
    }

    @Test
    public void unmatchedArgumentShowsHelp_NoMatched() throws Exception {
        final CommandLine commandLine = new CommandLine(new RootCommand());
        commandLine.addSubcommand(new TestCommand());
        commandLine.getSubcommands().get("test").addSubcommand(new TestSubCommand());
        commandLine.getSubcommands().get("test").addSubcommand(new TestSubCommandTwo());

        final CommandLine.UnmatchedArgumentException ex = getException(commandLine, "test", "idontexist");
        final String[] args = {"test", "idontexist"};
        handler.handleParseException(ex, args);

        assertThat(out.toString(StandardCharsets.UTF_8))
                .contains("Unknown command `test idontexist`")
                .contains("Usage: root test [--foo=<option>]")
                .doesNotContain("Did you mean one of");
    }

    @Test
    public void unmatchedArgumentInCommandsWithoutSubcommandsArePassedToDelegate() throws Exception {
        final CommandLine commandLine = new CommandLine(new RootCommand());
        commandLine.addSubcommand(new TestCommand());
        commandLine.getSubcommands().get("test").addSubcommand(new TestSubCommand());
        commandLine.getSubcommands().get("test").addSubcommand(new TestSubCommandTwo());

        commandLine.getSubcommands().get("test").usage(System.out);

        final CommandLine.UnmatchedArgumentException ex = getException(commandLine, "test", "sub", "idontexist");

        final String[] args = {"test", "sub", "idontexist"};
        handler.handleParseException(ex, args);

        verify(delegate).handleParseException(ex, args);
    }

    private static CommandLine.UnmatchedArgumentException getException(CommandLine commandLine, String... args) {
        final CommandLine.UnmatchedArgumentException ex;
        try {
            commandLine.parseArgs(args);
            throw new RuntimeException("Expecting parse to fail");
        } catch (CommandLine.UnmatchedArgumentException e) {
            ex = e;
        }
        return ex;
    }

    @CommandLine.Command(name = "root")
    private class RootCommand {

    }

    @CommandLine.Command(name = "test")
    private class TestCommand {

        @CommandLine.Option(names = "--foo")
        private String option;
    }

    @CommandLine.Command(name = "sub")
    private class TestSubCommand {

    }

    @CommandLine.Command(name = "sub2")
    private class TestSubCommandTwo {

    }
}