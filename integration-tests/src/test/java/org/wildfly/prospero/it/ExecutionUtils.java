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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;

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

    private static ExecutionResult execute(Execution execution) throws Exception {
        return execute(PROSPERO_SCRIPT_PATH, execution);
    }

    private static ExecutionResult execute(Path script, Execution execution) throws Exception {
        String[] execArray = mergeArrays(new String[] {script.toString()}, execution.args);
        Process process = new ProcessBuilder(execArray)
                .redirectErrorStream(true)
                .redirectOutput(new File("target/test-out.log"))
                .start();

        if (!process.waitFor(execution.timeLimit, execution.timeUnit)) {
            process.destroy();
            Assertions.fail("The process didn't complete in time.");
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

        public ExecutionResult execute() throws Exception {
            return ExecutionUtils.execute(this);
        }

        public ExecutionResult execute(Path script) throws Exception {
            return ExecutionUtils.execute(script, this);
        }
    }


    /**
     * Prospero execution results.
     */
    public static class ExecutionResult {

        private final Process process;

        public ExecutionResult(Process process) {
            this.process = process;
        }

        public void assertReturnCode(int expectedReturnCode) {
            try {
                assertThat(process.exitValue())
                        .overridingErrorMessage(Files.readString(Path.of("target/test-out.log")))
                        .isEqualTo(expectedReturnCode);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
