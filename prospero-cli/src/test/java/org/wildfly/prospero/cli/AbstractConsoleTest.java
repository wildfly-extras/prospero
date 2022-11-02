/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
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
        CliConsole console = new CliConsole() {
            @Override
            public boolean confirm(String prompt, String accepted, String cancelled) {
                return true;
            }
        };
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
