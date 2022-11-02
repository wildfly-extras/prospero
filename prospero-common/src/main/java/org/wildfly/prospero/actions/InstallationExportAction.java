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

package org.wildfly.prospero.actions;

import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class InstallationExportAction {

    private final Path installationDir;

    public InstallationExportAction(Path installationDir) {
        this.installationDir = installationDir;
    }

    public static void main(String[] args) throws Exception {
        String installation = args[0];
        String exportName = args[1];

        new InstallationExportAction(Paths.get(installation)).export(exportName);
    }

    public void export(String exportName) throws IOException, MetadataException {
        if (!installationDir.toFile().exists()) {
            throw Messages.MESSAGES.installationDirDoesNotExist(installationDir);
        }

        try (final InstallationMetadata metadataBundle = new InstallationMetadata(installationDir)) {

            metadataBundle.exportMetadataBundle(Paths.get(exportName));
        }
    }
}
