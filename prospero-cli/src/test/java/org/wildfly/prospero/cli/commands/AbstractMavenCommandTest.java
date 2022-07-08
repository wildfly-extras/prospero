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

package org.wildfly.prospero.cli.commands;

import org.junit.Test;
import org.wildfly.prospero.cli.AbstractConsoleTest;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class AbstractMavenCommandTest extends AbstractConsoleTest {

    @Test
    public void defaultMavenRepoIsUsedIfLocalRepoParameterNotUsed() throws Exception {
        int exitCode = commandLine.execute(getArgs());

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        MavenSessionManager msm = getCapturedSessionManager();
        assertEquals(MavenSessionManager.LOCAL_MAVEN_REPO, msm.getProvisioningRepo());
    }

    @Test
    public void overrideMavenRepoIfLocalRepoParameterPresent() throws Exception {
        int exitCode = commandLine.execute(getArgs(CliConstants.LOCAL_REPO, "test-path"));

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        MavenSessionManager msm = getCapturedSessionManager();
        assertEquals(Paths.get("test-path").toAbsolutePath(), msm.getProvisioningRepo());
    }

    private String[] getArgs(String... additional) {
        final List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList(getDefaultArguments()));
        args.addAll(Arrays.asList(additional));
        return args.toArray(new String[]{});
    }

    @Test
    public void useTemporaryMavenRepoIfNoLocalCacheParameterPresent() throws Exception {
        int exitCode = commandLine.execute(getArgs(CliConstants.NO_LOCAL_MAVEN_CACHE));

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        MavenSessionManager msm = getCapturedSessionManager();
        final Path provisioningRepo = msm.getProvisioningRepo();
        final Path defaultTempPath = Paths.get(System.getProperty("java.io.tmpdir"));
        assertTrue(provisioningRepo.toString(), provisioningRepo.startsWith(defaultTempPath));
    }

    @Test
    public void noLocalCacheAndLocalRepoAreMutuallyExclusive() throws Exception {
        int exitCode = commandLine.execute(getArgs(CliConstants.LOCAL_REPO, "test-path", CliConstants.NO_LOCAL_MAVEN_CACHE));

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
    }

    protected abstract MavenSessionManager getCapturedSessionManager() throws Exception;

    protected abstract String[] getDefaultArguments();
}
