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
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.actions.MetadataAction;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.model.RepositoryRef;
import picocli.CommandLine;

@CommandLine.Command(name = CliConstants.LIST)
public class RepositoryListCommand extends AbstractCommand {

    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> directory;

    public RepositoryListCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        Path installationDirectory = determineInstallationDirectory(directory);
        MetadataAction metadataAction = actionFactory.metadataActions(installationDirectory);
        List<RepositoryRef> repositories = metadataAction.getRepositories();

        // calculate maximum length of repository id strings, to make the list nicely alligned
        int maxRepoIdLength = repositories.stream()
                .map(r -> r.getId().length())
                .max(Integer::compareTo)
                .orElse(0);

        for (RepositoryRef repo: repositories) {
            console.println("%s\t%s", StringUtils.rightPad(repo.getId(), maxRepoIdLength), repo.getUrl());
        }
        return ReturnCodes.SUCCESS;
    }
}
