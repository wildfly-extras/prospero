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

import java.nio.file.Path;
import java.util.Optional;

import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.InstallationHistoryAction;
import org.wildfly.prospero.api.SavedState;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.options.LocalRepoOptions;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.REVERT,
        sortOptions = false
)
public class RevertCommand extends AbstractCommand {

    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> directory;

    @CommandLine.Option(names = CliConstants.REVISION, required = true)
    String revision;

    @CommandLine.ArgGroup(exclusive = true, headingKey = "localRepoOptions.heading")
    LocalRepoOptions localRepoOptions;

    @CommandLine.Option(names = CliConstants.OFFLINE)
    boolean offline;

    public RevertCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        final Path installationDirectory = determineInstallationDirectory(directory);
        final MavenSessionManager mavenSessionManager = new MavenSessionManager(LocalRepoOptions.getLocalRepo(localRepoOptions), offline);

        InstallationHistoryAction historyAction = actionFactory.history(installationDirectory, console);
        historyAction.rollback(new SavedState(revision), mavenSessionManager);
        return ReturnCodes.SUCCESS;
    }
}
