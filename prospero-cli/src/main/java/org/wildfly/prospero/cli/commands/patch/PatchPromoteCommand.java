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

package org.wildfly.prospero.cli.commands.patch;

import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.AbstractCommand;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.model.ChannelRef;
import picocli.CommandLine;

import java.net.URL;
import java.nio.file.Path;

@CommandLine.Command(
        name = "promote",
        sortOptions = false
)
public class PatchPromoteCommand extends AbstractCommand {
    @CommandLine.Option(
            names = CliConstants.PATCH_CHANNEL_NAME,
            required = true
    )
    private String name;

    @CommandLine.Option(
            names = "--archive",
            required = true
    )
    private Path archive;

    @CommandLine.Option(
            names = CliConstants.PATCH_REPOSITORY_URL,
            required = true
    )
    private URL url;


    public PatchPromoteCommand(Console console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        if (!isValidChannelCoordinate()) {
            console.error(CliMessages.MESSAGES.wrongChannelCoordinateFormat());
            return ReturnCodes.INVALID_ARGUMENTS;
        }
        // TODO: support remote repositories
        final ChannelRef coordinate = ChannelRef.fromString(name);
        actionFactory.promoter(console).promote(archive.normalize().toAbsolutePath(), url, coordinate);

        return ReturnCodes.SUCCESS;
    }

    private boolean isValidChannelCoordinate() {
        return name != null && !name.isEmpty() && ChannelRef.isValidCoordinate(name);
    }
}
