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

import org.jboss.galleon.ProvisioningException;
import org.jboss.logging.Logger;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.api.exceptions.NoChannelException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.cli.commands.CliConstants;
import picocli.CommandLine;

/**
 * Handles exceptions that happen during command executions.
 */
public class ExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {

    private final Logger logger = Logger.getLogger(this.getClass());
    private final Console console;

    public ExecutionExceptionHandler(Console console) {
        this.console = console;
    }

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult)
            throws Exception {
        if (ex instanceof NoChannelException) {
            console.error(ex.getMessage());
            console.error(CliMessages.MESSAGES.addChannels(CliConstants.CHANNEL));
            return ReturnCodes.INVALID_ARGUMENTS;
        }
        if (ex instanceof IllegalArgumentException || ex instanceof ArgumentParsingException) {
            // used to indicate invalid arguments
            console.error(ex.getMessage());
            return ReturnCodes.INVALID_ARGUMENTS;
        } else if (ex instanceof ProvisioningException || ex instanceof OperationException) {
            // provisioning error
            console.error(CliMessages.MESSAGES.errorWhileExecutingOperation(CliConstants.Commands.REVERT, ex.getMessage()));
            logger.error(CliMessages.MESSAGES.errorWhileExecutingOperation(CliConstants.Commands.INSTALL, ex.getMessage()), ex);
            return ReturnCodes.PROCESSING_ERROR;
        }

        // re-throw other exceptions
        throw ex;
    }
}
