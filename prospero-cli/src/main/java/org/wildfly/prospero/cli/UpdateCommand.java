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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.apache.commons.lang3.StringUtils;
import org.jboss.galleon.ProvisioningException;

class UpdateCommand implements Command {

    public static final String SELF_ARG = "self";
    public static final String JBOSS_MODULE_PATH = "module.path";
    public static final String PROSPERO_FP_GA = "org.wildfly.prospero:prospero-standalone-galleon-pack";
    public static final String PROSPERO_FP_ZIP = PROSPERO_FP_GA + "::zip";
    private final CliMain.ActionFactory actionFactory;

    public UpdateCommand(CliMain.ActionFactory actionFactory) {
        this.actionFactory = actionFactory;
    }

    @Override
    public String getOperationName() {
        return "update";
    }

    @Override
    public Set<String> getSupportedArguments() {
        return new HashSet<>(Arrays.asList(CliMain.TARGET_PATH_ARG, CliMain.DRY_RUN, CliMain.LOCAL_REPO, CliMain.OFFLINE, SELF_ARG));
    }

    @Override
    public void execute(Map<String, String> parsedArgs) throws ArgumentParsingException, OperationException {
        String dir = parsedArgs.get(CliMain.TARGET_PATH_ARG);
        final boolean dryRun = parseBooleanFlag(parsedArgs, CliMain.DRY_RUN);
        final String localRepo = parsedArgs.get(CliMain.LOCAL_REPO);
        final boolean offline = parseBooleanFlag(parsedArgs, CliMain.OFFLINE);
        final boolean selfUpdate = parseBooleanFlag(parsedArgs, SELF_ARG);

        if (selfUpdate) {
            if (StringUtils.isEmpty(dir)) {
                dir = detectInstallationPath();
            }

            verifyInstallationContainsOnlyProspero(dir);
        }

        if (StringUtils.isEmpty(dir)) {
            throw new ArgumentParsingException("Target dir argument (--%s) need to be set on update command", CliMain.TARGET_PATH_ARG);
        }

        if (offline && localRepo == null) {
            throw new ArgumentParsingException(Messages.offlineModeRequiresLocalRepo());
        }

        final Path targetPath = Paths.get(dir).toAbsolutePath();
        try {
            final MavenSessionManager mavenSessionManager;
            if (localRepo == null) {
                mavenSessionManager = new MavenSessionManager();
            } else {
                mavenSessionManager = new MavenSessionManager(Paths.get(localRepo).toAbsolutePath());
            }
            mavenSessionManager.setOffline(offline);

            if (!dryRun) {
                actionFactory.update(targetPath, mavenSessionManager).doUpdateAll();
            } else {
                actionFactory.update(targetPath, mavenSessionManager).listUpdates();
            }
        } catch (MetadataException | ProvisioningException e) {
            throw new OperationException("Error while executing update: " + e.getMessage(), e);
        }
    }

    private void verifyInstallationContainsOnlyProspero(String dir) throws ArgumentParsingException {
        try {
            final List<String> fpNames = GalleonUtils.getInstalledPacks(Paths.get(dir).toAbsolutePath());
            if (fpNames.size() != 1) {
                throw new ArgumentParsingException(Messages.unexpectedPackageInSelfUpdate(dir));
            }
            if (!fpNames.stream().allMatch(n-> PROSPERO_FP_ZIP.equals(n))) {
                throw new ArgumentParsingException(Messages.unexpectedPackageInSelfUpdate(dir));
            }
        } catch (ProvisioningException e) {
            throw new ArgumentParsingException(Messages.unableToParseSelfUpdateData(), e);
        }
    }

    private String detectInstallationPath() throws ArgumentParsingException {
        final String modulePath = System.getProperty(JBOSS_MODULE_PATH);
        if (modulePath == null) {
            throw new ArgumentParsingException(Messages.unableToLocateInstallation());
        }
        return Paths.get(modulePath).getParent().toString();
    }

    private boolean parseBooleanFlag(Map<String, String> parsedArgs, String self) {
        return parsedArgs.containsKey(self) && Boolean.parseBoolean(parsedArgs.get(self));
    }
}
