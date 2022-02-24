package com.redhat.prospero.cli;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class CliMainTest {

    @Mock
    private CliMain.ActionFactory actionFactory;

    @Test
    public void errorIfArgNameDoesntStartWithDoubleHyphens() throws Exception {
        try {
            new CliMain(actionFactory).handleArgs(new String[]{"install", "dir=test"});
            fail("Should have failed");
        } catch (ArgumentParsingException e) {
            assertEquals("Argument [dir=test] not recognized", e.getMessage());
        }
    }

    @Test
    public void errorIfArgumentHasNoValue() throws Exception {
        try {
            new CliMain(actionFactory).handleArgs(new String[]{"install", "--dir"});
            fail("Should have failed");
        } catch (ArgumentParsingException e) {
            assertEquals("Argument value cannot be empty", e.getMessage());
        }
    }

    @Test
    public void errorOnUnknownArgument() throws Exception {
        try {
            new CliMain(actionFactory).handleArgs(new String[]{"install", "--foo=bar"});
            fail("Should have failed");
        } catch (ArgumentParsingException e) {
            assertEquals("Argument name [--foo] not recognized", e.getMessage());
        }
    }

    @Test
    public void errorOnUnknownOperation() throws Exception {
        try {
            new CliMain(actionFactory).handleArgs(new String[]{"foo"});
            fail("Should have failed");
        } catch (ArgumentParsingException e) {
            assertEquals("Unknown operation foo", e.getMessage());
        }
    }

}