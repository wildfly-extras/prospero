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

import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;
import org.wildfly.prospero.cli.commands.CliConstants;

import java.net.URL;
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

    @Message("Update cancelled")
    String updateCancelled();

    @Message("Applying updates")
    String applyingUpdates();

    @Message("Choose [y/N]: ")
    String chooseYN();

    @Message("Update complete!")
    String updateComplete();

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
    String unexpectedPackageInSelfUpdate(String path);

    @Message("Unable to locate the installation folder to perform self-update.")
    String unableToLocateProsperoInstallation();

    @Message("Unable to perform self-update - unable to determine installed feature packs.")
    String unableToParseSelfUpdateData();

    @Message("Provisioning config argument (" + CliConstants.PROVISION_CONFIG + ") need to be set when using custom fpl")
    IllegalArgumentException prosperoConfigMandatoryWhenCustomFpl();

    @Message("Error while executing operation '%s': %s")
    String errorWhileExecutingOperation(String op, String exceptionMessage);

    @Message("No changes found")
    String noChangesFound();

    @Message("Error when processing command: ")
    String errorWhenProcessingCommand();

    @Message("%n[*] The update list contain one or more artifacts with lower versions then currently installed. Proceed with caution.%n%n")
    String possibleDowngrade();

    @Message("Repository '%s' removed.")
    String repositoryRemoved(String repoId);

    @Message("Repository '%s' added.")
    String repositoryAdded(String repoId);

    @Message("Channel '%s' added.")
    String channelAdded(String urlOrGav);

    @Message("Channel '%s' removed.")
    String channelRemoved(String urlOrGav);

    @Message("File referenced by [%s] doesn't exist: %s")
    String fileDoesntExist(String optionName, Path patchArchive);

    @Message("Path `%s` does not contain a server installation provisioned by prospero.")
    IllegalArgumentException invalidInstallationDir(Path path);

    @Message("Add required channels using [%s] argument.")
    String addChannels(String channel);

    @Message("Installation completed in %.2f seconds.")
    String installationCompleted(float time);

    @Message("Update completed in %.2f seconds.")
    String updateCompleted(float time);

    @Message("Only one of %s and %s can be set.")
    IllegalArgumentException exclusiveOptions(String option1, String option2);

    @Message("Patch repository `%s` already exist.")
    String patchesRepoExist(String patchesRepoId);

    @Message("Channel `%s` needs to have a groupId:artifactId format")
    String illegalChannel(String name);

    @Message("Unable to create a repository at `%s`.")
    String unableToCreateLocalRepository(URL url);

    @Message("Repository path `%s` is a file not a directory.")
    String repositoryIsNotDirectory(URL url);

    @Message("Channel coordinate must be provided in `groupId:artifactId` format")
    String wrongChannelCoordinateFormat();
}
