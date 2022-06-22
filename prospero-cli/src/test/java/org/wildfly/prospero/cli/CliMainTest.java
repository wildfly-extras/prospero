package org.wildfly.prospero.cli;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


@RunWith(MockitoJUnitRunner.class)
public class CliMainTest extends AbstractConsoleTest {

    @Test
    public void errorIfArgNameDoesntStartWithDoubleHyphens() {
        int exitCode = commandLine.execute("install", "--dir=test", "dir=test");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains("Unmatched argument at index 2: 'dir=test'"));
    }

    @Test
    public void errorIfArgumentHasNoValue() {
        int exitCode = commandLine.execute("install", "--dir");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput(), getErrorOutput().contains("Missing required parameter for option '--dir'"));
    }

    @Test
    public void errorOnUnknownArgument() {
        int exitCode = commandLine.execute("install", "--dir", "test", "--foo=bar");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains("Unknown option: '--foo=bar'"));
    }

    @Test
    public void errorOnUnknownOperation() {
        int exitCode = commandLine.execute("foo");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains("Unmatched argument at index 0: 'foo'"));
    }

}