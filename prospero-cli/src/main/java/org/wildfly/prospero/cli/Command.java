package org.wildfly.prospero.cli;

import org.wildfly.prospero.api.exceptions.OperationException;

import java.util.Map;
import java.util.Set;

public interface Command {
    String getOperationName();

    void execute(Map<String, String> parsedArgs) throws ArgumentParsingException, OperationException;

    Set<String> getSupportedArguments();
}
