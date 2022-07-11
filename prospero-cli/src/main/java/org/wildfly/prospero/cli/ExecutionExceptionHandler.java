package org.wildfly.prospero.cli;

import org.jboss.galleon.ProvisioningException;
import org.jboss.logging.Logger;
import org.wildfly.prospero.actions.Console;
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
        if (ex instanceof IllegalArgumentException || ex instanceof ArgumentParsingException) {
            // used to indicate invalid arguments
            console.error(ex.getMessage());
            return ReturnCodes.INVALID_ARGUMENTS;
        } else if (ex instanceof ProvisioningException || ex instanceof OperationException) {
            // provisioning error
            console.error(CliMessages.MESSAGES.errorWhileExecutingOperation(CliConstants.REVERT, ex.getMessage()));
            logger.error(CliMessages.MESSAGES.errorWhileExecutingOperation(CliConstants.INSTALL, ex.getMessage()), ex);
            return ReturnCodes.PROCESSING_ERROR;
        }

        // re-throw other exceptions
        throw ex;
    }
}
