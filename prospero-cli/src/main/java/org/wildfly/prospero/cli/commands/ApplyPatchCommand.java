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

import org.wildfly.prospero.actions.ApplyPatchAction;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@CommandLine.Command(
        name = CliConstants.APPLY_PATCH,
        sortOptions = false
)
public class ApplyPatchCommand extends AbstractCommand {

    @CommandLine.Option(names = CliConstants.DIR, required = true, order = 1)
    Path directory;

    @CommandLine.Option(names = CliConstants.PATCH_FILE, required = true, order = 2)
    Path patchArchive;

    @CommandLine.Option(
            names = CliConstants.LOCAL_REPO,
            order = 3
    )
    Optional<Path> localRepo;

    @CommandLine.Option(
            names = CliConstants.OFFLINE,
            order = 4
    )
    boolean offline;

    public ApplyPatchCommand(Console console, ActionFactory actionFactory) {
        super(console,actionFactory);
    }

    @Override
    public Integer call() throws Exception {

        if (!Files.exists(patchArchive)) {
            console.error(CliMessages.MESSAGES.fileDoesntExist(CliConstants.PATCH_FILE, patchArchive));
            return ReturnCodes.INVALID_ARGUMENTS;
        }

        final MavenSessionManager mavenSessionManager = new MavenSessionManager(localRepo, offline);

        final ApplyPatchAction applyPatchAction = actionFactory.applyPatch(directory, mavenSessionManager, console);
        applyPatchAction.apply(patchArchive);

        return ReturnCodes.SUCCESS;
    }
}
