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

package org.wildfly.prospero.it.cli;

import io.undertow.Undertow;
import io.undertow.server.handlers.resource.PathResourceManager;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.jboss.galleon.ProvisioningException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.commonapi.WfCoreTestBase;
import org.wildfly.prospero.promotion.ArtifactBundle;
import org.wildfly.prospero.it.ExecutionUtils;
import org.wildfly.prospero.test.MetadataTestUtils;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.wildfly.prospero.it.commonapi.WfCoreTestBase.REPOSITORY_MAVEN_CENTRAL;
import static io.undertow.Handlers.resource;

public class PatchingTest {

    public static final String PATCHED_GROUP_ID = "io.undertow";
    public static final String PATCHED_ARTIFACT_ID = "undertow-core";
    public static final String BASE_VERSION = WfCoreTestBase.UNDERTOW_VESION;
    public static final String PATCHED_VERSION = BASE_VERSION + "-patch001";
    public static final String PATCHED_JAR_NAME = PATCHED_ARTIFACT_ID + "-" + PATCHED_VERSION + ".jar";
    public static final Path UNDERTOW_MODULE_PATH = Paths.get("io", "undertow", "core", "main");
    public static final String TEST_CUSTOM_CHANNEL = "org.test.channel:custom";
    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private File targetDir;

    @Before
    public void setUp() throws IOException {
        targetDir = tempDir.newFolder();
    }

    @Test
    public void testLocalChannel() throws Exception {
        // install core
        installCore();

        // init-channel
        ExecutionUtils.prosperoExecution(CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_INIT_CHANNEL,
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS_LOCAL_CHANGES);

        // create bundle with upgraded artifact
        final Path customizationArchive = createCustomizationArchive();

        // promote bundle
        ExecutionUtils.prosperoExecution(CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_PROMOTE,
                        CliConstants.DIR, targetDir.getAbsolutePath(),
                        CliConstants.Y,
                        CliConstants.CUSTOMIZATION_ARCHIVE, customizationArchive.toString())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS_LOCAL_CHANGES);

        // apply update
        ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.PERFORM,
                        CliConstants.DIR, targetDir.getAbsolutePath(),
                        CliConstants.Y)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS_LOCAL_CHANGES);

        // verify updated
        assertEquals(PATCHED_VERSION, getInstalledUndertowVersion().get());

        assertTrue(Files.exists(undertowJarPath()));
    }

    private Optional<String> getInstalledUndertowVersion() throws MetadataException {
        final InstallationMetadata meta = InstallationMetadata.loadInstallation(targetDir.toPath());
        final Optional<String> undertowVersion = meta.getManifest().getStreams().stream()
                .filter(s -> s.getGroupId().equals(PATCHED_GROUP_ID) && s.getArtifactId().equals(PATCHED_ARTIFACT_ID))
                .map(Stream::getVersion)
                .findFirst();
        return undertowVersion;
    }

    @Test
    public void testRemoteChannel() throws Exception {
        // setup http
        final Path repositoryPath = tempDir.newFolder().toPath();
        final String repositoryUrl = "http://localhost:8888/";
        Undertow server = Undertow.builder()
                .addHttpListener(8888, "localhost")
                .setHandler(resource(new PathResourceManager(repositoryPath))
                        .setDirectoryListingEnabled(true))
                .build();
        server.start();

        try {
            // install core
            installCore();

            // init-channel
            ExecutionUtils.prosperoExecution(CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_INIT_CHANNEL,
                            CliConstants.DIR, targetDir.getAbsolutePath(),
                            CliConstants.CUSTOMIZATION_REPOSITORY_URL, repositoryUrl,
                            CliConstants.CHANNEL_MANIFEST, TEST_CUSTOM_CHANNEL)
                    .execute()
                    .assertReturnCode(ReturnCodes.SUCCESS_LOCAL_CHANGES);

            // create bundle with upgraded artifact
            final Path customizationArchive = createCustomizationArchive();

            // promote bundle
            ExecutionUtils.prosperoExecution(CliConstants.Commands.CHANNEL, CliConstants.Commands.CUSTOMIZATION_PROMOTE,
                            CliConstants.Y,
                            CliConstants.CUSTOMIZATION_ARCHIVE, customizationArchive.toString(),
                            CliConstants.CUSTOMIZATION_REPOSITORY_URL, repositoryPath.toUri().toURL().toString(),
                            CliConstants.CHANNEL_MANIFEST, TEST_CUSTOM_CHANNEL)
                    .execute()
                    .assertReturnCode(ReturnCodes.SUCCESS_LOCAL_CHANGES);

            // apply update
            ExecutionUtils.prosperoExecution(CliConstants.Commands.UPDATE, CliConstants.Commands.PERFORM,
                            CliConstants.DIR, targetDir.getAbsolutePath(),
                            CliConstants.Y)
                    .execute()
                    .assertReturnCode(ReturnCodes.SUCCESS_LOCAL_CHANGES);

            // verify updated
            assertEquals(PATCHED_VERSION, getInstalledUndertowVersion().get());

            assertTrue(Files.exists(undertowJarPath()));
        } finally {
            server.stop();
        }
    }

    private Path undertowJarPath() {
        final Path modules = Paths.get("modules", "system", "layers", "base");
        final Path undertowCoreJar = targetDir.toPath().resolve(modules).resolve(UNDERTOW_MODULE_PATH)
                .resolve(PATCHED_JAR_NAME);
        return undertowCoreJar;
    }

    private void installCore() throws Exception {
        Path channelsFile = MetadataTestUtils.prepareChannel("manifests/wfcore-base.yaml");

        ExecutionUtils.prosperoExecution(CliConstants.Commands.INSTALL,
                        CliConstants.CHANNELS, channelsFile.toString(),
                        CliConstants.FPL, "org.wildfly.core:wildfly-core-galleon-pack::zip",
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS_LOCAL_CHANGES);
    }

    private Path createCustomizationArchive() throws Exception {
        final File resolvedFile = resolveUndertowJar();
        final Path patchedFile = tempDir.getRoot().toPath().resolve(PATCHED_JAR_NAME);
        Files.copy(resolvedFile.toPath(), patchedFile);

        final DefaultArtifact testArtifact = new DefaultArtifact(PATCHED_GROUP_ID, PATCHED_ARTIFACT_ID,
                null, null, PATCHED_VERSION, null, patchedFile.toFile());
        return ArtifactBundle.createCustomizationArchive(Collections.singletonList(testArtifact), tempDir.newFile("archive.zip"));
    }

    private File resolveUndertowJar() throws ProvisioningException, ArtifactResolutionException {
        final MavenSessionManager msm = new MavenSessionManager();
        final RepositorySystem system = msm.newRepositorySystem();
        final DefaultRepositorySystemSession session = msm.newRepositorySystemSession(system);
        final ArtifactRequest req = new ArtifactRequest();
        req.setArtifact(new DefaultArtifact(PATCHED_GROUP_ID, PATCHED_ARTIFACT_ID, "jar", BASE_VERSION));
        req.setRepositories(Arrays.asList(WfCoreTestBase.toRemoteRepository(REPOSITORY_MAVEN_CENTRAL)));
        final ArtifactResult res = system.resolveArtifact(session, req);
        final File resolvedFile = res.getArtifact().getFile();
        return resolvedFile;
    }
}
