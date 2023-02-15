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

package org.wildfly.prospero.cli.commands;

import org.junit.Test;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.ReturnCodes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public abstract class AbstractMavenCommandTest extends AbstractConsoleTest {

    @Test
    public void defaultMavenRepoIsUsedIfLocalRepoParameterNotUsed() throws Exception {
        doLocalMock();
        int exitCode = commandLine.execute(getArgs());

        assertEquals(ReturnCodes.SUCCESS_LOCAL_CHANGES, exitCode);
        MavenOptions options = getCapturedMavenOptions();
        assertEquals(null, options.getLocalCache());
        assertFalse(options.isNoLocalCache());
    }

    @Test
    public void overrideMavenRepoIfLocalRepoParameterPresent() throws Exception {
        doLocalMock();

        int exitCode = commandLine.execute(getArgs(CliConstants.LOCAL_CACHE, "test-path"));

        assertEquals(ReturnCodes.SUCCESS_LOCAL_CHANGES, exitCode);
        MavenOptions options = getCapturedMavenOptions();
        assertEquals(Paths.get("test-path").toAbsolutePath(), options.getLocalCache());
    }

    private String[] getArgs(String... additional) {
        final List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList(getDefaultArguments()));
        args.addAll(Arrays.asList(additional));
        return args.toArray(new String[]{});
    }

    @Test
    public void useTemporaryMavenRepoIfNoLocalCacheParameterPresent() throws Exception {
        doLocalMock();
        int exitCode = commandLine.execute(getArgs(CliConstants.NO_LOCAL_MAVEN_CACHE));

        assertEquals(ReturnCodes.SUCCESS_LOCAL_CHANGES, exitCode);
        MavenOptions options = getCapturedMavenOptions();
        final Path provisioningRepo = options.getLocalCache();

        assertNull(provisioningRepo);
        assertTrue(options.isNoLocalCache());
    }

    @Test
    public void noLocalCacheAndLocalRepoAreMutuallyExclusive() throws Exception {
        int exitCode = commandLine.execute(getArgs(CliConstants.LOCAL_CACHE, "test-path", CliConstants.NO_LOCAL_MAVEN_CACHE));

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
    }

    protected abstract MavenOptions getCapturedMavenOptions() throws Exception;

    protected abstract String[] getDefaultArguments();

    protected void doLocalMock() throws Exception {

    }
}
