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

package org.wildfly.prospero.test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.Constants;
import org.jboss.galleon.state.ProvisionedFeaturePack;
import org.jboss.galleon.state.ProvisionedState;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.xml.ProvisionedStateXmlWriter;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.model.RepositoryRef;

public final class MetadataTestUtils {

    public static final Path MANIFEST_FILE_PATH =
            Paths.get(InstallationMetadata.METADATA_DIR, InstallationMetadata.MANIFEST_FILE_NAME);
    public static final Path PROVISION_CONFIG_FILE_PATH =
            Paths.get(InstallationMetadata.METADATA_DIR, InstallationMetadata.PROSPERO_CONFIG_FILE_NAME);

    private MetadataTestUtils() {
    }

    public static Channel createManifest(Collection<Stream> streams) {
        return new Channel("manifest", null, null, null, streams);
    }

    public static InstallationMetadata createInstallationMetadata(Path installation) throws MetadataException {
        return createInstallationMetadata(installation, createManifest(null), Collections.emptyList(), Collections.emptyList());
    }

    public static InstallationMetadata createInstallationMetadata(Path installation, List<ChannelRef> channels,
            List<RemoteRepository> repositories) throws MetadataException {
        return createInstallationMetadata(installation, createManifest(null), channels, repositories);
    }

    public static InstallationMetadata createInstallationMetadata(Path installation, Channel manifest, List<ChannelRef> channels,
            List<RemoteRepository> repositories) throws MetadataException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtils.deleteQuietly(installation.toFile())));
        final InstallationMetadata metadata = new InstallationMetadata(installation, manifest, channels, repositories);
        metadata.recordProvision(true);
        return metadata;
    }

    public static ProvisionedState createGalleonProvisionedState(Path installation, String... featurePacks)
            throws XMLStreamException, IOException {
        final ProvisionedState.Builder builder = ProvisionedState.builder();
        for (String fp : featurePacks) {
            builder.addFeaturePack(ProvisionedFeaturePack.builder(FeaturePackLocation.fromString(fp).getFPID()).build());
        }
        ProvisionedState state = builder.build();
        ProvisionedStateXmlWriter.getInstance().write(state,
                installation.resolve(Constants.PROVISIONED_STATE_DIR).resolve(Constants.PROVISIONED_STATE_XML));
        return state;
    }

    public static URL prepareProvisionConfigAsUrl(String channelDescriptor) throws IOException {
        final Path provisionConfigFile = Files.createTempFile("channels", "yaml");
        provisionConfigFile.toFile().deleteOnExit();

        final Path path = prepareProvisionConfigAsUrl(provisionConfigFile, channelDescriptor);
        return path.toUri().toURL();
    }

    public static Path prepareProvisionConfig(String channelDescriptor) throws IOException {
        final Path provisionConfigFile = Files.createTempFile("channels", "yaml").toAbsolutePath();
        provisionConfigFile.toFile().deleteOnExit();

        return prepareProvisionConfigAsUrl(provisionConfigFile, channelDescriptor);
    }

    public static Path prepareProvisionConfigAsUrl(Path provisionConfigFile, String... channelDescriptor)
            throws IOException {
        List<URL> channelUrls = Arrays.stream(channelDescriptor)
                .map(d->MetadataTestUtils.class.getClassLoader().getResource(d))
                .collect(Collectors.toList());
        List<ChannelRef> channels = new ArrayList<>();
        List<RepositoryRef> repositories = defaultRemoteRepositories().stream()
                .map(r->new RepositoryRef(r.getId(), r.getUrl())).collect(Collectors.toList());
        for (int i=0; i<channelUrls.size(); i++) {
            channels.add(new ChannelRef(null, channelUrls.get(i).toString()));
        }
        new ProsperoConfig(channels, repositories).writeConfig(provisionConfigFile.toFile());

        return provisionConfigFile;
    }

    public static List<RemoteRepository> defaultRemoteRepositories() {
        return Arrays.asList(
                new RemoteRepository.Builder("maven-central", "default", "https://repo1.maven.org/maven2/").build(),
                new RemoteRepository.Builder("nexus", "default", "https://repository.jboss.org/nexus/content/groups/public-jboss").build(),
                new RemoteRepository.Builder("maven-redhat-ga", "default", "https://maven.repository.redhat.com/ga").build()
        );
    }
}
