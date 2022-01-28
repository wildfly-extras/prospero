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

package com.redhat.prospero.cli.actions;

import com.redhat.prospero.api.InstallationMetadata;
import com.redhat.prospero.api.MetadataException;
import org.jboss.galleon.ProvisioningException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class InstallationExport {

    private final Path installationDir;

    public InstallationExport(Path installationDir) {
        this.installationDir = installationDir;
    }

    public static void main(String[] args) throws Exception {
        String installation = args[0];
        String exportName = args[1];

        new InstallationExport(Paths.get(installation)).export(exportName);
    }

    public void export(String exportName) throws ProvisioningException, IOException, MetadataException {
        if (!installationDir.toFile().exists()) {
            throw new ProvisioningException("Installation dir " + installationDir + " doesn't exist");
        }

        final InstallationMetadata metadataBundle = new InstallationMetadata(installationDir);

        metadataBundle.exportMetadataBundle(Paths.get(exportName));
    }
}
