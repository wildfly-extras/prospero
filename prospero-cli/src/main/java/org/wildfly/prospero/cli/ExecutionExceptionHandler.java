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

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.error.MarkedYAMLException;
import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.ArtifactCoordinate;
import org.wildfly.channel.ChannelMetadataCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.channel.spi.ArtifactIdentifier;
import org.wildfly.channel.spi.SignatureValidator;
import org.wildfly.prospero.api.ArtifactUtils;
import org.wildfly.prospero.api.exceptions.ApplyCandidateException;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.ChannelDefinitionException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.StreamNotFoundException;
import org.wildfly.prospero.api.exceptions.UnresolvedChannelMetadataException;
import org.wildfly.prospero.api.exceptions.NoChannelException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

import javax.net.ssl.SSLHandshakeException;
import javax.xml.stream.XMLStreamException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

/**
 * Handles exceptions that happen during command executions.
 */
public class ExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {

    private static final Set<String> OFFLINE_REPOSITORIES = Set.of(MavenSessionManager.AETHER_OFFLINE_PROTOCOLS_VALUE.split(","));
    private final CliConsole console;
    private final boolean isVerbose;

    public ExecutionExceptionHandler(CliConsole console, boolean isVerbose) {
        this.console = console;
        this.isVerbose = isVerbose;
    }

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parseResult)
            throws Exception {
        Integer returnCode = null;
        if (ex instanceof NoChannelException) {
            console.error(CliMessages.MESSAGES.errorHeader(ex.getLocalizedMessage()));
            console.error(CliMessages.MESSAGES.addChannels(CliConstants.CHANNEL_MANIFEST));
            returnCode = ReturnCodes.INVALID_ARGUMENTS;
        }
        if (ex instanceof IllegalArgumentException) {
            // used to indicate invalid arguments
            console.error(CliMessages.MESSAGES.errorHeader(ex.getLocalizedMessage()));
            returnCode = ReturnCodes.INVALID_ARGUMENTS;
        } else if (ex instanceof ArgumentParsingException) {
            ArgumentParsingException ape = (ArgumentParsingException) ex;
            // used to indicate invalid arguments
            console.error(CliMessages.MESSAGES.errorHeader(ape.getLocalizedMessage()));
            for (String detail : ape.getDetails()) {
                console.error("  " + detail);
            }
            returnCode = ReturnCodes.INVALID_ARGUMENTS;
        } else if (ex instanceof OperationException) {
            if (ex instanceof ChannelDefinitionException) {
                if (ex.getCause() != null) {
                    if (ex.getCause().getCause() instanceof SSLHandshakeException) {
                        console.error(CliMessages.MESSAGES.errorSSL());
                    } else if (ex.getCause().getCause() instanceof UnknownHostException) {
                        console.error(CliMessages.MESSAGES.errorUnknownHost());
                    } else {
                    console.error(CliMessages.MESSAGES.errorHeader(ex.getLocalizedMessage()));
                    }
                }
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
            } else if (ex instanceof MetadataException) {
                console.error(ex.getLocalizedMessage());
                if (ex.getCause() != null && (ex.getCause() instanceof MarkedYAMLException || ex.getCause() instanceof JsonMappingException)) {
                    console.error(ex.getCause().getLocalizedMessage());
                }
            } else if (ex instanceof ApplyCandidateException) {
                ApplyCandidateException ace = (ApplyCandidateException) ex;
                console.error(System.lineSeparator() + ace.getMessage());

                if (ace.isRollbackSuccessful()) {
                    console.error(System.lineSeparator() + CliMessages.MESSAGES.candidateApplyRollbackSuccess());
                } else {
                    console.error(System.lineSeparator() + CliMessages.MESSAGES.candidateApplyRollbackFailure(ace.getBackupPath()));
                }

            } else {
                console.error(CliMessages.MESSAGES.errorHeader(ex.getLocalizedMessage()));
            }
            returnCode = ReturnCodes.PROCESSING_ERROR;
        } else if (ex instanceof ProvisioningException) {
            handleProvisioningException((ProvisioningException)ex);
            returnCode = ReturnCodes.PROCESSING_ERROR;
        } else if (ex instanceof SignatureValidator.SignatureException) {
            handleSignatureValidationException((SignatureValidator.SignatureException) ex);
            returnCode = ReturnCodes.PROCESSING_ERROR;
        }


        if (returnCode != null) {
            if (isVerbose) {
                console.error("");
                ex.printStackTrace(console.getErrOut());
            }
            return returnCode;
        } else {
            // re-throw other exceptions
            throw ex;
        }
    }

    private void handleProvisioningException(ProvisioningException ex) {
        // new line to return from last provisioning progress line
        console.error("\n");
        final String message = ex.getMessage();

        if (ex.getCause() instanceof SignatureValidator.SignatureException) {
            handleSignatureValidationException((SignatureValidator.SignatureException) ex.getCause());
        } else if (message.startsWith("Failed to parse")) {
            // the error coming from Galleon is not translated, so try to figure out what went wrong and show translated message
            String path = message.substring("Failed to parse".length()+1).trim();
            console.error(CliMessages.MESSAGES.parsingError(path));
            if (ex.getCause() instanceof XMLStreamException) {
                console.error(ex.getCause().getLocalizedMessage());
            }
        } else {
            console.error(CliMessages.MESSAGES.errorHeader(ex.getLocalizedMessage()));
        }
    }

    private void handleSignatureValidationException(SignatureValidator.SignatureException ex) {
        final ArtifactIdentifier artifact = ex.getSignatureResult().getResource();
        switch (ex.getSignatureResult().getResult()) {
            case NO_SIGNATURE:
                console.error(String.format("Unable to find a required signature for artifact %s", artifact.getDescription()));
                break;
            case NO_MATCHING_CERT:
                console.error(String.format("Unable to find a trusted certificate for key ID %s used to sign %s", ex.getSignatureResult().getKeyId(),
                        artifact.getDescription()));
                console.error("If you wish to proceed, please review your trusted certificates.");
                break;
            case INVALID:
                console.error(String.format("The signature for artifact %s is invalid. The artifact might be corrupted or tampered with.",
                        artifact.getDescription()));
                break;
            case REVOKED:
                console.error(String.format("The key used to sign the artifact %s has been revoked with a message:%n  %s.",
                        artifact.getDescription(), ex.getSignatureResult().getMessage()));
                break;
            default:
                console.error(CliMessages.MESSAGES.errorHeader(ex.getCause().getLocalizedMessage()));
                break;
        }
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
            final List<ArtifactResolutionAnalyzer.Result> results = new ArtifactResolutionAnalyzer().analyze(ex);
            for (ArtifactResolutionAnalyzer.Result result : results) {
                console.error(String.format("  * %s [%s]", result.getCoords(), result.getStatus()));
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
