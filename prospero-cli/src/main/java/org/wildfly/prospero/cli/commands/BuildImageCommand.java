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

package org.wildfly.prospero.cli.commands;

import static com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer.DEFAULT_MODIFICATION_TIME_PROVIDER;

import java.nio.file.Path;
import java.util.Optional;

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.FilePermissionsProvider;
import com.google.cloud.tools.jib.api.buildplan.OwnershipProvider;
import org.jboss.logging.Logger;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliConsole;
import org.wildfly.prospero.cli.ReturnCodes;
import picocli.CommandLine;

@CommandLine.Command(
        name = CliConstants.Commands.BUILD_IMAGE,
        sortOptions = false
)
public class BuildImageCommand extends AbstractCommand {

    private static final Logger log = Logger.getLogger(BuildImageCommand.class);

    @CommandLine.Option(names = CliConstants.NAME)
    String name;

    @CommandLine.Option(names = CliConstants.DIR)
    Optional<Path> directory;

    @CommandLine.Option(names = CliConstants.USER)
    Optional<String> user;

    @CommandLine.Option(names = CliConstants.PASSWORD)
    Optional<String> password;

    @CommandLine.Option(names = CliConstants.RUNTIME_VERSION)
    Optional<String> runtimeVersion;

    private static final FilePermissionsProvider PERMISSIONS_PROVIDER = (sourcePath, destinationPath) -> FilePermissions.fromOctalString("775");

    private static final OwnershipProvider JBOSS_ROOT_OWNER = (sourcePath, destinationPath) -> "jboss:root";

    public BuildImageCommand(CliConsole console, ActionFactory actionFactory) {
        super(console, actionFactory);
    }

    @Override
    public Integer call() throws Exception {
        Path installationDirectory = determineInstallationDirectory(directory);

        String baseImage = "quay.io/wildfly/wildfly-runtime:" + runtimeVersion.orElse("latest");
        System.out.println("Creating image with installation at " + installationDirectory + " from " + baseImage);

        RegistryImage registryImage = RegistryImage.named(name);
        if (user.isPresent() && password.isPresent()) {
            registryImage.addCredential(user.get(), password.get());
        }

        FileEntriesLayer layer = FileEntriesLayer.builder()
                .setName("wildfly")
                .addEntryRecursive(installationDirectory, AbsoluteUnixPath.get("/opt/server/"),
                        PERMISSIONS_PROVIDER,
                        DEFAULT_MODIFICATION_TIME_PROVIDER,
                        JBOSS_ROOT_OWNER)
                .build();

        Jib.from(baseImage)
                .addFileEntriesLayer(layer)
                .containerize(Containerizer.to(registryImage));

        return ReturnCodes.SUCCESS;
    }

}
