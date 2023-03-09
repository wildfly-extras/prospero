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

package org.wildfly.prospero;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;

import org.jboss.galleon.ProvisioningException;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;
import org.wildfly.channel.InvalidChannelMetadataException;
import org.wildfly.prospero.api.exceptions.ArtifactPromoteException;
import org.wildfly.prospero.api.exceptions.ChannelDefinitionException;
import org.wildfly.prospero.api.exceptions.InvalidUpdateCandidateException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.NoChannelException;
import org.wildfly.prospero.api.exceptions.ProvisioningRuntimeException;

@MessageBundle(projectCode = "PRSP")
public interface Messages {

    public Messages MESSAGES = org.jboss.logging.Messages.getBundle(Messages.class);

    @Message("Given path '%s' is a regular file. An empty directory or a non-existing path must be given.")
    IllegalArgumentException dirMustBeDirectory(Path path);

    @Message("Invalid channel manifest definition")
    ChannelDefinitionException invalidManifest(@Cause InvalidChannelMetadataException e);

    @Message("Invalid channel definition")
    ChannelDefinitionException invalidChannel(@Cause InvalidChannelMetadataException e);

    @Message("Can't install into a non empty directory '%s'. Use `update` command if you want to modify existing installation.")
    IllegalArgumentException cannotInstallIntoNonEmptyDirectory(Path path);

    @Message("Installation dir '%s' doesn't exist")
    IllegalArgumentException installationDirDoesNotExist(Path path);

    @Message("Installation dir '%s' already exists")
    ProvisioningException installationDirAlreadyExists(Path installDir);

    @Message("Given configuration doesn't reference any channel or channel manifest.")
    NoChannelException noChannelReference();

    @Message("Invalid channel: Channel '%s' doesn't reference a manifest.")
    NoChannelException noChannelManifestReference(String name);

    @Message("Pre-defined FPL [%s] doesn't specify any channels and no explicit channels were given.")
    NoChannelException fplDefinitionDoesntContainChannel(String fpl);

    @Message("Channel '%s' is already present.")
    MetadataException channelExists(String channelName);

    @Message("Channel with name [%s] cannot be found.")
    MetadataException channelNotFound(String channelName);

    @Message("Channel name cannot be empty.")
    MetadataException emptyChannelName();

    @Message("Promoting artifacts to %s:")
    String promotingArtifacts(URL targetRepository);

    @Message("Provided FPL has invalid format `%s`.")
    String invalidFpl(String fplText);

    @Message("Unable to parse server configuration at '%s'")
    MetadataException unableToParseConfiguration(Path path, @Cause Throwable e);

    @Message("Unable to parse server configuration at '%s'")
    MetadataException unableToParseConfigurationUri(URI uri, @Cause Throwable e);

    @Message("Unable to save server configuration at '%s'")
    MetadataException unableToSaveConfiguration(Path path, @Cause Exception e);

    @Message("Unable to close the update store.")
    MetadataException unableToCloseStore(@Cause Exception e);

    @Message("Path `%s` does not contain a server installation provisioned by prospero.")
    IllegalArgumentException invalidInstallationDir(Path path);

    @Message("Unable to create history store at [%s]")
    MetadataException unableToCreateHistoryStorage(Path path, @Cause Exception e);

    @Message("Unable to access history store at [%s]")
    MetadataException unableToAccessHistoryStorage(Path path, @Cause Exception e);

    @Message("Unable to read file at [%s]")
    MetadataException unableToReadFile(Path path, @Cause Exception e);

    @Message("Unable to download file from [%s]")
    MetadataException unableToDownloadFile(URL url, @Cause IOException e);

    @Message("Unable to write file at [%s]")
    MetadataException unableToWriteFile(Path path, @Cause Exception e);

    // provisioning errors
    @Message("Unable to create temporary cache for provisioning cache folder.")
    ProvisioningException unableToCreateCache(@Cause Exception e);

    @Message("Failed to initiate maven repository system")
    ProvisioningRuntimeException failedToInitMaven(@Cause Throwable exception);

    @Message("Invalide URL [%s]")
    IllegalArgumentException invalidUrl(String text, @Cause Exception e);

    @Message("Incomplete configuration: If the FPL is not one of predefined names (%s) a channel must be given.")
    IllegalArgumentException predefinedFplOrChannelRequired(String availableFpls);

    @Message("Incomplete configuration: neither FPL nor Galleon provisioning config was given.")
    IllegalArgumentException fplNorGalleonConfigWereSet();

    @Message("Provided metadata bundle [%s] is missing one or more entries")
    IllegalArgumentException incompleteMetadataBundle(Path path);

    @Message("Found unexpected artifact [%s]")
    ProvisioningRuntimeException unexpectedArtifact(String gav);

    @Message("Unable to resolve artifact")
    String unableToResolve();

    @Message("File already exists [%s]")
    IllegalArgumentException fileAlreadyExists(Path path);

    @Message("Promoting to non-file repositories is not currently supported")
    IllegalArgumentException unsupportedPromotionTarget();

    @Message("Wrong format of custom channel version [%s]")
    IllegalArgumentException wrongVersionFormat(String baseVersion);

    @Message("Custom channel version exceeded limit [%s]")
    IllegalArgumentException versionLimitExceeded(String baseVersion);

    @Message("Cannot create bundle without artifacts.")
    IllegalArgumentException noArtifactsToPackage();

    @Message("Channel reference has to use Maven GA.")
    IllegalArgumentException nonMavenChannelRef();

    @Message("Unable to promote artifacts to [%s].")
    ArtifactPromoteException unableToPromote(URL target, @Cause Exception e);

    @Message("Unable to parse the customization bundle [%s].")
    ArtifactPromoteException unableToParseCustomizationBundle(Path path, @Cause Exception e);

    @Message("At least one repository must be set when using the manifest option.")
    IllegalArgumentException repositoriesMustBeSetWithManifest();

    @Message("Out file [%s] exists already!")
    IllegalArgumentException outFileExists(Path outPath);

    @Message("Installation metadata is exported to [%s].")
    String installationExported(Path outPath);

    @Message("The file: [%s] to be restored does not exist.")
    String restoreFileNotExisted(Path path);

    @Message("Installation meta was restored from: [%s] to [%s].")
    String installationMetaRestored(Path restorePath, Path installPath);

    @Message("Malformed URL in substituted value : %s from %s")
    MetadataException invalidPropertySubstitutionValue(String substituted, String url);

    @Message("The installation at %s is not a valid update for %s")
    InvalidUpdateCandidateException invalidUpdateCandidate(Path update, Path installation);

    @Message("The installation at %s is not a valid rollback for %s")
    InvalidUpdateCandidateException invalidRollbackCandidate(Path update, Path installation);

    @Message("The requested state %s does not exist in server's history.")
    MetadataException savedStateNotFound(String name);

    @Message("Unable to create temporary directory")
    ProvisioningException unableToCreateTemporaryDirectory(@Cause Throwable t);
}
