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

import com.redhat.prospero.api.MetadataException;
import com.redhat.prospero.api.exceptions.OperationException;
import com.redhat.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;

class UpdateCommand implements Command {

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
        return new HashSet<>(Arrays.asList(CliMain.TARGET_PATH_ARG, CliMain.DRY_RUN, CliMain.LOCAL_REPO, CliMain.OFFLINE));
    }

    @Override
    public void execute(Map<String, String> parsedArgs) throws ArgumentParsingException, OperationException {
        String dir = parsedArgs.get(CliMain.TARGET_PATH_ARG);
        Boolean dryRun = parsedArgs.containsKey(CliMain.DRY_RUN) ? Boolean.parseBoolean(parsedArgs.get(CliMain.DRY_RUN)) : false;
        String localRepo = parsedArgs.get(CliMain.LOCAL_REPO);
        boolean offline = parsedArgs.containsKey(CliMain.OFFLINE) ? Boolean.parseBoolean(parsedArgs.get(CliMain.OFFLINE)) : false;
        if (dir == null || dir.isEmpty()) {
            throw new ArgumentParsingException("Target dir argument (--%s) need to be set on update command", CliMain.TARGET_PATH_ARG);
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
}
