package org.wildfly.prospero.it;

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

    private static final String PROSPERO_SCRIPT_PATH = isWindows()?
            Paths.get("..", "prospero.bat").toString():
            Paths.get("..", "prospero").toString();

    public static Execution prosperoExecution(String... args) {
        return new Execution(args);
    }

    private static ExecutionResult execute(Execution execution) throws Exception {
        String[] execArray = mergeArrays(new String[] {PROSPERO_SCRIPT_PATH}, execution.args);
        Process process = Runtime.getRuntime().exec(execArray);

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

    private static boolean isWindows() {
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
            assertThat(process.exitValue())
                    .overridingErrorMessage(new ProcessErrorStreamReader(process))
                    .isEqualTo(expectedReturnCode);
        }
    }
}
