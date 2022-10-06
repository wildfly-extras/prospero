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

import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.ChannelDefinitionException;
import org.wildfly.prospero.api.exceptions.NoChannelException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.model.RepositoryRef;
import picocli.CommandLine;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles exceptions that happen during command executions.
 */
public class ExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {
    private final Console console;

    public ExecutionExceptionHandler(Console console) {
        this.console = console;
    }

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult)
            throws Exception {
        if (ex instanceof NoChannelException) {
            console.error("ERROR: " + ex.getMessage());
            console.error(CliMessages.MESSAGES.addChannels(CliConstants.CHANNEL));
            return ReturnCodes.INVALID_ARGUMENTS;
        }
        if (ex instanceof IllegalArgumentException || ex instanceof ArgumentParsingException) {
            // used to indicate invalid arguments
            console.error("ERROR: " + ex.getMessage());
            return ReturnCodes.INVALID_ARGUMENTS;
        } else if (ex instanceof OperationException) {
            if (ex instanceof ChannelDefinitionException) {
                console.error("ERROR: " + ex.getMessage());
                console.error(((ChannelDefinitionException) ex).getValidationMessages());
            } else if (ex instanceof ArtifactResolutionException) {
                // new line to return from last provisioning progress line
                console.error("\n");
                ArtifactResolutionException are = (ArtifactResolutionException)ex;
                if (!are.failedArtifacts().isEmpty()) {
                    console.error("ERROR: " + "Could not find artifact(s): [" + missingArtifacts(are.failedArtifacts()) + "].");
                    if (!are.attemptedRepositories().isEmpty()) {
                        console.error("  Artifacts are not available in [" + repositories(are.attemptedRepositories()) + "]");
                    }
                    if (!are.offlineRepositories().isEmpty()) {
                        console.error("  Following repositories are offline and the artifacts were not downloaded from them previously: [" + repositories(are.offlineRepositories()));
                    }
                } else {
                    console.error("ERROR: " + ex.getMessage());
                }
            } else {
                console.error("ERROR: " + ex.getMessage());
            }
            return ReturnCodes.PROCESSING_ERROR;
        } else if (ex instanceof ProvisioningException) {
            // new line to return from last provisioning progress line
            console.error("\n");
            console.error("ERROR: " + ex.getMessage());
            return ReturnCodes.PROCESSING_ERROR;
        }

        // re-throw other exceptions
        throw ex;
    }

    private String repositories(Set<RepositoryRef> coordinates) {
        return coordinates.stream().map(r->r.getId() + " (" + r.getUrl() + ")").collect(Collectors.joining(", "));
    }

    private String missingArtifacts(Set<ArtifactCoordinate> coordinates) {
        return coordinates.stream().map(this::shortName).collect(Collectors.joining(", "));
    }

    private String shortName(ArtifactCoordinate coord) {
        StringBuilder sb = new StringBuilder();
        sb.append(coord.getGroupId()).append(":").append(coord.getArtifactId());
        sb.append(":").append(coord.getVersion());
        return sb.toString();
    }
}
