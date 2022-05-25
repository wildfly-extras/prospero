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

package com.redhat.prospero.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.redhat.prospero.api.exceptions.MetadataException;
import com.redhat.prospero.api.ProvisioningDefinition;
import com.redhat.prospero.api.exceptions.OperationException;
import com.redhat.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;

class InstallCommand implements Command {
    public static final String DEFINITION_ARG = "definition";

    private final CliMain.ActionFactory actionFactory;

    InstallCommand(CliMain.ActionFactory actionFactory) {
        this.actionFactory = actionFactory;
    }

    @Override
    public String getOperationName() {
        return "install";
    }

    @Override
    public Set<String> getSupportedArguments() {
        return new HashSet<>(Arrays.asList(CliMain.TARGET_PATH_ARG, CliMain.FPL_ARG, CliMain.CHANNEL_FILE_ARG,
                CliMain.CHANNEL_REPO, CliMain.CHANNEL, CliMain.LOCAL_REPO, CliMain.OFFLINE, DEFINITION_ARG));
    }

    @Override
    public void execute(Map<String, String> parsedArgs) throws ArgumentParsingException, OperationException {
        String dir = parsedArgs.get(CliMain.TARGET_PATH_ARG);
        String provisionDefinition = parsedArgs.get(DEFINITION_ARG);
        String fpl = parsedArgs.get(CliMain.FPL_ARG);
        String channelFile = parsedArgs.get(CliMain.CHANNEL_FILE_ARG);
        String channelRepo = parsedArgs.get(CliMain.CHANNEL_REPO);
        String localRepo = parsedArgs.get(CliMain.LOCAL_REPO);
        String channel = parsedArgs.get(CliMain.CHANNEL);
        boolean offline = parsedArgs.containsKey(CliMain.OFFLINE) ? Boolean.parseBoolean(parsedArgs.get(CliMain.OFFLINE)) : false;

        if (dir == null || dir.isEmpty()) {
            throw new ArgumentParsingException("Target dir argument (--%s) need to be set on install command", CliMain.TARGET_PATH_ARG);
        }
        final boolean usingFpl = fpl != null && !fpl.isEmpty();
        final boolean usingProvDefinition = provisionDefinition != null && !provisionDefinition.isEmpty();
        if (!usingFpl && !usingProvDefinition) {
            throw new ArgumentParsingException("Feature pack name argument (--%s) need to be set on install command", CliMain.FPL_ARG);
        }
        if (usingFpl && usingProvDefinition) {
            throw new ArgumentParsingException("Feature pack name argument (--%s) cannot be used together with provisioning definition argument (--%s)", CliMain.FPL_ARG, InstallCommand.DEFINITION_ARG);
        }

        if (!usingProvDefinition && isStandardFpl(fpl) && (channelFile == null || channelFile.isEmpty())) {
            throw new ArgumentParsingException("Channel file argument (--%s) need to be set when using custom fpl", CliMain.CHANNEL_FILE_ARG);
        }

        try {
            final Path installationDir = Paths.get(dir).toAbsolutePath();
            final MavenSessionManager mavenSessionManager;
            if (localRepo == null) {
                mavenSessionManager = new MavenSessionManager();
            } else {
                mavenSessionManager = new MavenSessionManager(Paths.get(localRepo).toAbsolutePath());
            }
            mavenSessionManager.setOffline(offline);

            final ProvisioningDefinition provisioningDefinition = ProvisioningDefinition.builder()
                    .setFpl(fpl)
                    .setChannel(channel)
                    .setChannelsFile(channelFile == null ? null : Paths.get(channelFile).toAbsolutePath())
                    .setChannelRepo(channelRepo)
                    .setDefinitionFile(provisionDefinition == null ? null : Paths.get(provisionDefinition).toAbsolutePath())
                    .build();

            actionFactory.install(installationDir, mavenSessionManager).provision(provisioningDefinition);
        } catch (ProvisioningException | MetadataException e) {
            throw new OperationException("Error while executing installation: " + e.getMessage(), e);
        }
    }

    private boolean isStandardFpl(String fpl) {
        return !fpl.equals("eap") && !fpl.equals("eap-7.4") && !fpl.equals("wildfly");
    }
}
