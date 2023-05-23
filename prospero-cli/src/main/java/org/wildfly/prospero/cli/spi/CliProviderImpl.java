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

package org.wildfly.prospero.cli.spi;

import org.wildfly.installationmanager.spi.OsShell;
import org.wildfly.prospero.spi.internal.CliProvider;
import org.wildfly.prospero.cli.DistributionInfo;
import org.wildfly.prospero.cli.commands.CliConstants;

import java.nio.file.Path;

/**
 * implementation of {@link CliProvider}
 */
public class CliProviderImpl implements CliProvider {

    @Override
    public String getScriptName(OsShell shell) {
        final String suffix;
        switch (shell) {
            case Linux:
                suffix = ".sh";
                break;
            case WindowsBash:
                suffix = ".bat";
                break;
            case WindowsPowerShell:
                suffix = ".ps1";
                break;
            default:
                throw new IllegalArgumentException(String.format("The requested shell %s is not supported", shell));
        }
        return DistributionInfo.DIST_NAME + suffix;
    }

    @Override
    public String getApplyUpdateCommand(Path installationPath, Path candidatePath) {
        return CliConstants.Commands.UPDATE + " " + CliConstants.Commands.APPLY + " "
                + CliConstants.DIR + " " + escape(installationPath.toAbsolutePath()) + " "
                + CliConstants.CANDIDATE_DIR + " " + escape(candidatePath.toAbsolutePath()) + " "
                + CliConstants.YES;
    }

    @Override
    public String getApplyRevertCommand(Path installationPath, Path candidatePath) {
        return CliConstants.Commands.REVERT + " " + CliConstants.Commands.APPLY + " "
                + CliConstants.DIR + " " + escape(installationPath.toAbsolutePath()) + " "
                + CliConstants.CANDIDATE_DIR + " " + escape(candidatePath.toAbsolutePath()) + " "
                + CliConstants.YES;
    }

    private String escape(Path absolutePath) {
        return "\"" + absolutePath.toString() + "\"";
    }
}
