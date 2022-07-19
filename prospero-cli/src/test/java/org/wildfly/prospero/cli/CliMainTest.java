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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.cli.commands.CliConstants;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


@RunWith(MockitoJUnitRunner.class)
public class CliMainTest extends AbstractConsoleTest {

    @Test
    public void errorIfArgNameDoesntStartWithDoubleHyphens() {
        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR + "=test", "dir=test");
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains("Unmatched argument at index 2: 'dir=test'"));
    }

    @Test
    public void errorIfArgumentHasNoValue() {
        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR);
        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput(), getErrorOutput().contains("Missing required parameter for option '--dir'"));
    }

    @Test
    public void errorOnUnknownArgument() {
        int exitCode = commandLine.execute(CliConstants.Commands.INSTALL, CliConstants.DIR, "test", "--foo=bar");
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