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

package org.wildfly.prospero.it.commonapi;

import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.actions.InstallationExportAction;
import org.wildfly.prospero.actions.InstallationRestoreAction;
import org.wildfly.prospero.actions.ProvisioningAction;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.it.AcceptingConsole;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.test.MetadataTestUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class InstallationRestoreActionTest extends WfCoreTestBase {
    private Path restoredServerDir;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        restoredServerDir = temp.newFolder().toPath().resolve("restored-server");
    }

    @Test
    public void restoreInstallation() throws Exception {
        final Path provisionConfigFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setProvisionConfig(provisionConfigFile)
                .build();
        new ProvisioningAction(outputPath, mavenSessionManager, new AcceptingConsole())
                .provision(provisioningDefinition.toProvisioningConfig(), provisioningDefinition.getChannels());

        prepareInstallerConfig(outputPath.resolve(MetadataTestUtils.PROVISION_CONFIG_FILE_PATH), CHANNEL_COMPONENT_UPDATES, CHANNEL_BASE_CORE_19);

        new InstallationExportAction(outputPath).export("target/bundle.zip");

        new InstallationRestoreAction(restoredServerDir, mavenSessionManager, new AcceptingConsole()).restore(Paths.get("target/bundle.zip"));

        final Optional<Artifact> wildflyCliArtifact = readArtifactFromManifest("org.wildfly.core", "wildfly-cli");
        assertEquals(BASE_VERSION, wildflyCliArtifact.get().getVersion());
    }

    private Optional<Artifact> readArtifactFromManifest(String groupId, String artifactId) throws IOException {
        final File manifestFile = restoredServerDir.resolve(MetadataTestUtils.MANIFEST_FILE_PATH).toFile();
        return ManifestYamlSupport.parse(manifestFile).getStreams()
                .stream().filter((a) -> a.getGroupId().equals(groupId) && a.getArtifactId().equals(artifactId))
                .findFirst()
                .map(s->new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion()));
    }

    // config including correct channel names
    private static void prepareInstallerConfig(Path provisionConfigFile, String... channelDescriptor)
            throws IOException {
        List<URL> channelUrls = Arrays.stream(channelDescriptor)
                .map(d->InstallationRestoreActionTest.class.getClassLoader().getResource(d))
                .collect(Collectors.toList());
        List<Channel> channels = new ArrayList<>();
        List<Repository> repositories1 = defaultRemoteRepositories().stream()
                .map(r->new Repository(r.getId(), r.getUrl())).collect(Collectors.toList());
        for (int i = 0; i< channelUrls.size(); i++) {
            channels.add(new Channel("channel-" + i, "", null, null, repositories1,
                    new ChannelManifestCoordinate(channelUrls.get(i))));
        }

        new ProsperoConfig(channels).writeConfig(provisionConfigFile);
    }

}
