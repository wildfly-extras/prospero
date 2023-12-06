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

import static org.wildfly.prospero.cli.CliMain.execute;
import static org.wildfly.prospero.cli.CliMain.logException;

/**
 * Variant of {@link CliMain} that doesn't call {@link System#exit(int)}.
 * Meant for embedded use cases, for example Maven build executions.
 */
public class CliMainNoExit {

    public static void main(String[] args) {
        try {
            int exitCode = execute(args);
            if (exitCode != 0) {
                System.err.println(CliMessages.MESSAGES.unexpectedExitWhenProcessingCommand(exitCode));
                CliMain.logger.error(CliMessages.MESSAGES.unexpectedExitWhenProcessingCommand(exitCode));
            }
        } catch (Exception e) {
            logException(e);
        }
    }
}
