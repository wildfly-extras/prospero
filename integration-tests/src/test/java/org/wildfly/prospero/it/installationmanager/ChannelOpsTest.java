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

package org.wildfly.prospero.it.installationmanager;

import org.junit.Test;
import org.wildfly.installationmanager.Channel;
import org.wildfly.installationmanager.MavenOptions;
import org.wildfly.installationmanager.Repository;
import org.wildfly.installationmanager.spi.InstallationManager;
import org.wildfly.prospero.api.ProvisioningDefinition;
import org.wildfly.prospero.it.commonapi.WfCoreTestBase;
import org.wildfly.prospero.spi.ProsperoInstallationManagerFactory;
import org.wildfly.prospero.test.MetadataTestUtils;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ChannelOpsTest extends WfCoreTestBase {

    @Test
    public void listChannels() throws Exception {
        // installCore
        Path channelsFile = MetadataTestUtils.prepareProvisionConfig(CHANNEL_BASE_CORE_19);

        final ProvisioningDefinition provisioningDefinition = defaultWfCoreDefinition()
                .setChannelCoordinates(channelsFile.toString())
                .build();
        installation.provision(provisioningDefinition.toProvisioningConfig(),
                provisioningDefinition.resolveChannels(CHANNELS_RESOLVER_FACTORY));

        final InstallationManager manager = new ProsperoInstallationManagerFactory().create(outputPath, new MavenOptions(MavenSessionManager.LOCAL_MAVEN_REPO, false));

        final Collection<Channel> channels = manager.listChannels();

        assertThat(channels).containsExactly(
                new Channel("channel-0", List.of(new Repository("maven-central", "https://repo1.maven.org/maven2/"),
                        new Repository("nexus", "https://repository.jboss.org/nexus/content/groups/public-jboss"),
                        new Repository("maven-redhat-ga", "https://maven.repository.redhat.com/ga")),
                        ChannelOpsTest.class.getClassLoader().getResource(CHANNEL_BASE_CORE_19))
        );

        // add channel
        manager.addChannel(new Channel("test", List.of(new Repository("test-repo", "http://test.te/repo")),
                "foo:bar"));

        assertThat(manager.listChannels()).containsExactly(
                new Channel("channel-0", List.of(new Repository("maven-central", "https://repo1.maven.org/maven2/"),
                        new Repository("nexus", "https://repository.jboss.org/nexus/content/groups/public-jboss"),
                        new Repository("maven-redhat-ga", "https://maven.repository.redhat.com/ga")),
                        ChannelOpsTest.class.getClassLoader().getResource(CHANNEL_BASE_CORE_19)),
                new Channel("test", List.of(new Repository("test-repo", "http://test.te/repo")),
                        "foo:bar")
        );

        // edit channel
        manager.changeChannel("test",
                new Channel("test", List.of(new Repository("test-repo2", "http://test.te/repo")),
                        "foo:bar2"));

        assertThat(manager.listChannels()).containsExactly(
                new Channel("channel-0", List.of(new Repository("maven-central", "https://repo1.maven.org/maven2/"),
                        new Repository("nexus", "https://repository.jboss.org/nexus/content/groups/public-jboss"),
                        new Repository("maven-redhat-ga", "https://maven.repository.redhat.com/ga")),
                        ChannelOpsTest.class.getClassLoader().getResource(CHANNEL_BASE_CORE_19)),
                new Channel("test", List.of(new Repository("test-repo2", "http://test.te/repo")),
                        "foo:bar2")
        );

        // remove channel
        manager.removeChannel("test");
        assertThat(channels).containsExactly(
                new Channel("channel-0", List.of(new Repository("maven-central", "https://repo1.maven.org/maven2/"),
                        new Repository("nexus", "https://repository.jboss.org/nexus/content/groups/public-jboss"),
                        new Repository("maven-redhat-ga", "https://maven.repository.redhat.com/ga")),
                        ChannelOpsTest.class.getClassLoader().getResource(CHANNEL_BASE_CORE_19))
        );
    }

}
