package org.wildfly.prospero.cli;

import org.junit.Before;
import org.junit.Rule;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import picocli.CommandLine;

public class AbstractConsoleTest {

    @Rule
    public SystemOutRule systemOutRule = new SystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public SystemErrRule systemErrRule = new SystemErrRule().enableLog().muteForSuccessfulTests();

    protected CommandLine commandLine;

    @Before
    public void setUp() throws Exception {
        CliConsole console = new CliConsole();
        commandLine = CliMain.createCommandLine(console, createActionFactory());
    }

    protected ActionFactory createActionFactory() {
        return new ActionFactory();
    }

    public String getStandardOutput() {
        return systemOutRule.getLog();
    }

    public String getErrorOutput() {
        return systemErrRule.getLog();
    }
}
