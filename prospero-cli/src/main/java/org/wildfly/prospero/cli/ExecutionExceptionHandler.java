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

import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.ChannelMetadataCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.api.ArtifactUtils;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.ChannelDefinitionException;
import org.wildfly.prospero.api.exceptions.StreamNotFoundException;
import org.wildfly.prospero.api.exceptions.UnresolvedChannelMetadataException;
import org.wildfly.prospero.api.exceptions.NoChannelException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

/**
 * Handles exceptions that happen during command executions.
 */
public class ExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {

    private static final Set<String> OFFLINE_REPOSITORIES = Set.of(MavenSessionManager.AETHER_OFFLINE_PROTOCOLS_VALUE.split(","));
    private final CliConsole console;

    public ExecutionExceptionHandler(CliConsole console) {
        this.console = console;
    }

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult)
            throws Exception {
        if (ex instanceof NoChannelException) {
            console.error(CliMessages.MESSAGES.errorHeader(ex.getLocalizedMessage()));
            console.error(CliMessages.MESSAGES.addChannels(CliConstants.CHANNEL_MANIFEST));
            return ReturnCodes.INVALID_ARGUMENTS;
        }
        if (ex instanceof IllegalArgumentException) {
            // used to indicate invalid arguments
            console.error(CliMessages.MESSAGES.errorHeader(ex.getLocalizedMessage()));
            return ReturnCodes.INVALID_ARGUMENTS;
        } else if (ex instanceof ArgumentParsingException) {
            ArgumentParsingException ape = (ArgumentParsingException) ex;
            // used to indicate invalid arguments
            console.error(CliMessages.MESSAGES.errorHeader(ape.getLocalizedMessage()));
            for (String detail : ape.getDetails()) {
                console.error("  " + detail);
            }
            return ReturnCodes.INVALID_ARGUMENTS;
        } else if (ex instanceof OperationException) {
            if (ex instanceof ChannelDefinitionException) {
                console.error(CliMessages.MESSAGES.errorHeader(ex.getLocalizedMessage()));
                console.error(((ChannelDefinitionException) ex).getValidationMessages());
            } else if (ex instanceof StreamNotFoundException) {
                StreamNotFoundException snfe = (StreamNotFoundException) ex;
                printResolutionException(snfe);
            } else if (ex instanceof ArtifactResolutionException) {
                ArtifactResolutionException are = (ArtifactResolutionException) ex;
                printResolutionException(are);
            } else if (ex instanceof UnresolvedChannelMetadataException) {
                UnresolvedChannelMetadataException mcme = (UnresolvedChannelMetadataException) ex;
                printMissingMetadataException(mcme);
            } else {
                console.error(CliMessages.MESSAGES.errorHeader(ex.getLocalizedMessage()));
            }
            return ReturnCodes.PROCESSING_ERROR;
        } else if (ex instanceof ProvisioningException) {
            // new line to return from last provisioning progress line
            console.error("\n");
            console.error(CliMessages.MESSAGES.errorHeader(ex.getLocalizedMessage()));
            return ReturnCodes.PROCESSING_ERROR;
        }

        // re-throw other exceptions
        throw ex;
    }

    private void printMissingMetadataException(UnresolvedChannelMetadataException ex) {
        console.error(CliMessages.MESSAGES.errorHeader(CliMessages.MESSAGES.unableToResolveChannelMetadata()));
        for (ChannelMetadataCoordinate missingArtifact : ex.getMissingArtifacts()) {
            console.error("  * " + ArtifactUtils.printCoordinate(missingArtifact));
        }
        printRepositories(ex.getRepositories(), ex.isOffline());
    }

    private void printResolutionException(StreamNotFoundException ex) {
        // new line to return from last provisioning progress line
        console.error("\n");
        if (!ex.getMissingArtifacts().isEmpty()) {
            console.error(CliMessages.MESSAGES.errorHeader(CliMessages.MESSAGES.streamsNotFound()));
            for (ArtifactCoordinate missingArtifact : ex.getMissingArtifacts()) {
                console.error("  * " + ArtifactUtils.printStream(missingArtifact));
            }
        } else {
            console.error(CliMessages.MESSAGES.errorHeader(ex.getLocalizedMessage()));

        }
    }

    private void printResolutionException(ArtifactResolutionException ex) {
        // new line to return from last provisioning progress line
        console.error("\n");
        if (!ex.getMissingArtifacts().isEmpty()) {
            console.error(CliMessages.MESSAGES.errorHeader(CliMessages.MESSAGES.unableToResolveArtifacts()));
            for (ArtifactCoordinate missingArtifact : ex.getMissingArtifacts()) {
                console.error("  * " + ArtifactUtils.printCoordinate(missingArtifact));
            }

            printRepositories(ex.getRepositories(), ex.isOffline());
        } else {
            console.error(CliMessages.MESSAGES.errorHeader(ex.getLocalizedMessage()));
        }
    }

    private void printRepositories(Set<Repository> repositories, boolean b) {
        if (!repositories.isEmpty()) {
            console.error(" " + CliMessages.MESSAGES.attemptedRepositories());
            for (Repository repository : repositories) {
                boolean isOffline = isOffline(b, repository);
                String repo = String.format("%s::%s", repository.getId(), repository.getUrl());
                console.error("  *" + repo + (isOffline ? " ["+ CliMessages.MESSAGES.offline() + "]" : ""));
            }
        }
    }

    private static boolean isOffline(boolean offline, Repository repository) {
        if (!offline) {
            return false;
        } else {
            try {
                return !OFFLINE_REPOSITORIES.contains(new URL(repository.getUrl()).getProtocol());
            } catch (MalformedURLException e) {
                // ignore
                return true;
            }
        }
    }
}
