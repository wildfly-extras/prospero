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

package org.wildfly.prospero.it;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.testcontainers.shaded.org.apache.commons.lang3.tuple.Pair;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Utilities for executing prospero commands from the integration tests module.
 *
 * This class currently uses CLI shaded JAR to run prospero. It depends on the CLI module being built before the
 * integration test module.
 */
public class ExecutionUtils {

    private static final Path PROSPERO_SCRIPT_PATH = isWindows()?
            Paths.get("..", "prospero.bat"):
            Paths.get("..", "prospero");

    public static Execution prosperoExecution(String... args) {
        return new Execution(args);
    }

    private static ExecutionResult execute(Execution execution, Collection<Pair<String,String>> prompts) throws Exception {
        return execute(PROSPERO_SCRIPT_PATH, execution, prompts);
    }

    @SuppressWarnings("BusyWait")
    private static ExecutionResult execute(Path script, Execution execution, Collection<Pair<String,String>> prompts) throws Exception {
        String[] execArray = mergeArrays(new String[] {script.toString()}, execution.args);
        Process process = new ProcessBuilder(execArray)
                .redirectErrorStream(true)
                .start();

        final Iterator<Pair<String, String>> iterator = prompts.iterator();
        Pair<String, String> nextPrompt;
        if (iterator.hasNext()) {
            nextPrompt = iterator.next();
        } else {
            nextPrompt = null;
        }

        FileUtils.deleteQuietly(new File("target/test-out.log"));

        final InputStream inputStream = process.getInputStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
             FileWriter writer = new FileWriter(new File("target/test-out.log"));
             PrintWriter console = new PrintWriter(process.getOutputStream())) {
            final long startTime = System.currentTimeMillis();

            StringBuilder lineBuffer = new StringBuilder();
            while (process.isAlive()) {
                // read new lines
                while (reader.ready()) {
                    final int c = reader.read();
                    lineBuffer.append((char)c);
                    String line = lineBuffer.toString();
                    // we need to check the line before line separator for the prompt
                    if (nextPrompt != null && line.equals(nextPrompt.getKey())) {
                        // if they match expected prompts -> respond in scripted way
                        console.println(nextPrompt.getValue());
                        console.flush();

                        if (iterator.hasNext()) {
                            nextPrompt = iterator.next();
                        } else {
                            nextPrompt = null;
                        }
                        lineBuffer = new StringBuilder();
                    } else if (c != '\n') {
                        // we're looking for an end
                        writer.write(c);
                        writer.flush();
                    } else {
                        writer.write(c);
                        writer.flush();
                        lineBuffer = new StringBuilder();
                    }
                }
                // if the timeout happened, exit and kill the process
                if (System.currentTimeMillis() - startTime > execution.timeUnit.toMillis(execution.timeLimit)) {
                    if (!lineBuffer.isEmpty()) {
                        FileUtils.writeStringToFile(new File("target/test-out.log"), lineBuffer + System.lineSeparator(), StandardCharsets.UTF_8, true);
                    }

                    process.destroy();
                    Assertions.fail("The process didn't complete in time.");
                }
                // finally sleep some more
                Thread.sleep(100);
            }

            while (reader.ready()) {
                final int c = reader.read();
                writer.write(c);
            }

            if (nextPrompt != null) {
                Assertions.fail("Expected prompt %s to be used in the command, but it wasn't.", nextPrompt.getKey());
            }
        }

        return new ExecutionResult(process);
    }

    private static String[] mergeArrays(String[] a1, String[] a2) {
        String[] finalArray = Arrays.copyOf(a1, a1.length + a2.length);
        System.arraycopy(a2, 0, finalArray, a1.length, a2.length);
        return finalArray;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows");
    }

    /**
     * Parameters for prospero execution.
     */
    public static class Execution {

        private int timeLimit = 10;
        private TimeUnit timeUnit = TimeUnit.MINUTES;
        private final String[] args;

        private final ArrayList<Pair<String, String>> prompts = new ArrayList<>();

        public Execution(String[] args) {
            this.args = args;
        }

        /**
         * @param newLimit time limit for prospero execution in minutes
         */
        public Execution withTimeLimit(int newLimit, TimeUnit timeUnit) {
            this.timeLimit = newLimit;
            this.timeUnit = timeUnit;
            return this;
        }

        public Execution withPrompt(String prompt, String response) {
            prompts.add(Pair.of(prompt, response));
            return this;
        }

        public ExecutionResult execute() throws Exception {
            return ExecutionUtils.execute(this, prompts);
        }

        public ExecutionResult execute(Path script) throws Exception {
            return ExecutionUtils.execute(script, this, prompts);
        }
    }

    private static String getCommandOutput() throws IOException {
        return Files.readString(Path.of("target/test-out.log"));
    }


    /**
     * Prospero execution results.
     */
    public static class ExecutionResult {

        private final Process process;

        public ExecutionResult(Process process) {
            this.process = process;
        }

        public ExecutionResult assertReturnCode(int expectedReturnCode) throws IOException {
            assertThat(process.exitValue())
                    .overridingErrorMessage(getCommandOutput())
                    .isEqualTo(expectedReturnCode);
            return this;
        }

        public ExecutionResult assertErrorContains(String text) throws IOException {
            final String output = getCommandOutput();
            assertThat(output).contains(text);
            return this;
        }

        public String getCommandOutput() throws IOException {
            return ExecutionUtils.getCommandOutput();
        }
    }
}
