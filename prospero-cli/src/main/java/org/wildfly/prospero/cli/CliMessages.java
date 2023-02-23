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

import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;
import org.wildfly.prospero.actions.ApplyCandidateAction;
import org.wildfly.prospero.cli.commands.CliConstants;

import java.nio.file.Path;

@MessageBundle(projectCode = "PRSP-CLI")
public interface CliMessages {

    public CliMessages MESSAGES = Messages.getBundle(CliMessages.class);

    //
    // CliConsole strings
    //

    @Message("Resolving feature-pack")
    String resolvingFeaturePack();

    @Message("Installing packages")
    String installingPackages();

    @Message("Generating configuration")
    String generatingConfiguration();

    @Message("Installing JBoss modules")
    String installingJBossModules();

    @Message("Feature-packs resolved.")
    String featurePacksResolved();

    @Message("Packages installed.")
    String packagesInstalled();

    @Message("Configurations generated.")
    String configurationsGenerated();

    @Message("JBoss modules installed.")
    String jbossModulesInstalled();

    @Message("No updates found.")
    String noUpdatesFound();

    @Message("Updates found: ")
    String updatesFound();

    @Message("Continue with update [y/N]: ")
    String continueWithUpdate();

    @Message("Continue with building update [y/N]: ")
    String continueWithBuildUpdate();

    @Message("Update cancelled")
    String updateCancelled();

    @Message("Build update cancelled")
    String buildUpdateCancelled();

    @Message("Applying updates")
    String applyingUpdates();

    @Message("Building updates")
    String buildingUpdates();

    @Message("Choose [y/N]: ")
    String chooseYN();

    @Message("Update complete!")
    String updateComplete();

    @Message("Build update complete!")
    String buildUpdateComplete();

    // this would be used to determine user answer to [y/n] questions
    @Message("y")
    String yesShortcut();

    // this would be used to determine user answer to [y/n] questions
    @Message("n")
    String noShortcut();

    //
    // Other strings
    //

    @Message("Unable to perform self-update - folder `%s` contains unexpected feature packs.")
    ArgumentParsingException unexpectedPackageInSelfUpdate(String path);

    @Message("Unable to locate the installation folder to perform self-update.")
    ArgumentParsingException unableToLocateProsperoInstallation();

    @Message("Unable to perform self-update - unable to determine installed feature packs.")
    ArgumentParsingException unableToParseSelfUpdateData(@Cause Exception e);

    @Message("No channel or channel manifest were specified.")
    IllegalArgumentException channelsMandatoryWhenCustomFpl();

    @Message("No changes found")
    String noChangesFound();

    @Message("Error when processing command: ")
    String errorWhenProcessingCommand();

    @Message("%n[*] The update list contain one or more artifacts with lower versions then currently installed. Proceed with caution.%n%n")
    String possibleDowngrade();

    @Message("Channel '%s' added.")
    String channelAdded(String urlOrGav);

    @Message("Channel '%s' removed.")
    String channelRemoved(String urlOrGav);

    /**
     * @see #invalidInstallationDir(Path)
     */
    @Message("Path `%s` does not contain a server installation provisioned by the %s.")
    IllegalArgumentException invalidInstallationDir(Path path, String distName);

    default IllegalArgumentException invalidInstallationDir(Path path) {
        return invalidInstallationDir(path, DistributionInfo.DIST_NAME);
    }

    /**
     * @see #invalidInstallationDirMaybeUseDirOption(Path)
     */
    @Message("Path `%s` does not contain a server installation provisioned by the %s."
            + " Maybe you forgot to specify path to the installation (" + CliConstants.DIR + ")?")
    IllegalArgumentException invalidInstallationDirMaybeUseDirOption(Path path, String distName);

    default IllegalArgumentException invalidInstallationDirMaybeUseDirOption(Path path) {
        return invalidInstallationDirMaybeUseDirOption(path, DistributionInfo.DIST_NAME);
    }

    @Message("Add required channels using [%s] argument.")
    String addChannels(String channel);

    @Message("Operation completed in %.2f seconds.")
    String operationCompleted(float time);

    @Message("Only one of %s and %s can be set.")
    IllegalArgumentException exclusiveOptions(String option1, String option2);

    @Message("Custom repository `%s` already exist.")
    String customizationRepoExist(String repositoryId);

    @Message("Channel `%s` needs to have a groupId:artifactId format")
    String illegalChannel(String name);

    @Message("Unable to create a repository at `%s`.")
    String unableToCreateLocalRepository(Path repositoryPath);

    @Message("Repository path `%s` is a file not a directory.")
    ArgumentParsingException repositoryIsNotDirectory(Path repo);

    @Message("Channel coordinate must be provided in `groupId:artifactId` format")
    String wrongChannelCoordinateFormat();

    @Message("Unable to determine custom channel and repository.%nUse `%s` and `%s` to provide correct values.")
    String noCustomizationConfigFound(String channelParam, String repoParam);

    @Message("Continue with promoting artifacts: [y/N]: ")
    String continuePromote();

    @Message("Promoting artifacts.")
    String continuePromoteAccepted();

    @Message("Operation cancelled.")
    String continuePromoteRejected();

    @Message("Custom channel already exists.")
    String customizationChannelAlreadyExists();

    @Message("Registering custom channel `%s`")
    String registeringCustomChannel(String name);

    @Message("Repository definition [%s] is invalid. The definition format should be [id::url]")
    ArgumentParsingException invalidRepositoryDefinition(String repoKey);

    // start - changes diff
    @Message("manifest")
    String manifest();

    @Message("repositories")
    String repositories();

    @Message("Updated")
    String changeUpdated();

    @Message("Added")
    String changeAdded();

    @Message("Removed")
    String changeRemoved();

    @Message("Updates")
    String diffUpdates();

    @Message("Configuration changes")
    String diffConfigChanges();

    @Message("artifact")
    String artifactChangeType();

    @Message("channel")
    String channelChangeType();

    @Message("Conflicting changes detected in the update:")
    String conflictingChangesDetected();

    @Message("Server at [%s] is not a valid update candidate.")
    IllegalArgumentException invalidUpdateCandidate(Path updateDir);

    @Message("Unable to apply update.%n  Installation at [%s] has been updated since the update candidate [%s] was created.")
    IllegalArgumentException updateCandidateStateNotMatched(Path targetDir, Path updateDir);

    @Message("Unable to apply update.%n  The candidate at [%s] was not prepared for %s operation.")
    IllegalArgumentException updateCandidateWrongType(Path updateDir, ApplyCandidateAction.Type operation);

    @Message("Unable to apply update.%n  Installation at [%s] doesn't have a candidate marker file.")
    IllegalArgumentException notCandidate(Path updateDir);


    @Message("The target path needs to point to an empty, writable folder.")
    IllegalArgumentException nonEmptyTargetFolder();

    // end - changes diff

    @Message("Accept the agreement(s) [y/N]")
    String acceptAgreements();

    @Message("Installation cancelled")
    String installationCancelled();

    @Message("To install the requested server, following Agreements need to be accepted:")
    String listAgreementsHeader();

    @Message("The requested software does not require any Agreements.")
    String noAgreementsNeeded();

    @Message("The Agreement(s) has been accepted via %s")
    String agreementSkipped(String optionName);
}
