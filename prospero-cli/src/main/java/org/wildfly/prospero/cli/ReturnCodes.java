package org.wildfly.prospero.cli;

import picocli.CommandLine;

public final class ReturnCodes {

    private ReturnCodes() {
    }

    public static final int SUCCESS = CommandLine.ExitCode.OK;
    public static final int INVALID_ARGUMENTS = CommandLine.ExitCode.USAGE;
    public static final int PROCESSING_ERROR = CommandLine.ExitCode.SOFTWARE;
}
