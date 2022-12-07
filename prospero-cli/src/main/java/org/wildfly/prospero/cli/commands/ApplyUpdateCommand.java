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

import java.nio.file.Path;
import java.util.Optional;

import org.wildfly.prospero.actions.ApplyUpdateAction;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.Commands.APPLY_UPDATE,
        sortOptions = false
)
public class ApplyUpdateCommand extends AbstractCommand {

    // directory to apply the update to. Contains old version of the server
    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> installationDirectory;

    // directory with the updated server.
    @CommandLine.Option(names = CliConstants.UPDATE_DIR, required = true)
    Path updateDir;

    public ApplyUpdateCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        final long startTime = System.currentTimeMillis();

        // TODO: verify server is stopped
        // TODO: verify updateDir contains server and is an update

        final Path installationDir = determineInstallationDirectory(installationDirectory);

        ApplyUpdateAction applyUpdateAction = actionFactory.applyUpdate(installationDir.toAbsolutePath(), updateDir.toAbsolutePath());
        applyUpdateAction.applyUpdate();

        final float totalTime = (System.currentTimeMillis() - startTime) / 1000f;
        console.println(CliMessages.MESSAGES.operationCompleted(totalTime));

        return ReturnCodes.SUCCESS;
    }
}
