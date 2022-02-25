package com.redhat.prospero.cli;

import com.redhat.prospero.api.exceptions.OperationException;

import java.util.Map;
import java.util.Set;

public interface Command {
    String getOperationName();

    void execute(Map<String, String> parsedArgs) throws ArgumentParsingException, OperationException;

    Set<String> getSupportedArguments();
}
