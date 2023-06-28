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

package org.wildfly.prospero.test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.Constants;
import org.jboss.galleon.state.ProvisionedFeaturePack;
import org.jboss.galleon.state.ProvisionedState;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.xml.ProvisionedStateXmlWriter;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.ChannelManifestCoordinate;
import org.wildfly.channel.ChannelManifestMapper;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.Repository;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.model.ProsperoConfig;

public final class MetadataTestUtils {

    public static final Path MANIFEST_FILE_PATH =
            Paths.get(ProsperoMetadataUtils.METADATA_DIR, ProsperoMetadataUtils.MANIFEST_FILE_NAME);
    public static final Path INSTALLER_CHANNELS_FILE_PATH =
            Paths.get(ProsperoMetadataUtils.METADATA_DIR, ProsperoMetadataUtils.INSTALLER_CHANNELS_FILE_NAME);

    private MetadataTestUtils() {
    }

    public static ChannelManifest createManifest(Collection<Stream> streams) {
        return new ChannelManifest("manifest", null, null, streams);
    }

    public static void createManifest(String name, List<Stream> streams, Path metadataDir) throws IOException {
        String txt = ChannelManifestMapper.toYaml(new ChannelManifest(name, null, null, streams));
        // workaround for Windows
        txt = txt.replace("\n", System.lineSeparator());

        Files.writeString(metadataDir.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME), txt);
    }

    public static void createChannel(String name, String mavenCoordinate, List<String> repositories, Path metadataDir) throws IOException {
        final ChannelManifestCoordinate coordinate;
        final String[] splitCoord = mavenCoordinate.split(":");
        if (splitCoord.length == 2) {
            coordinate = new ChannelManifestCoordinate(splitCoord[0], splitCoord[1]);
        } else if (splitCoord.length == 3) {
            coordinate = new ChannelManifestCoordinate(splitCoord[0], splitCoord[1],
                    splitCoord[2]);
        } else {
            throw new IllegalArgumentException("Invalid maven coordinate of manifest " + mavenCoordinate);
        }
        final Channel.Builder builder = new Channel.Builder()
                .setName(name)
                .setManifestCoordinate(coordinate);

        for (int i = 0; i < repositories.size(); i++) {
            builder.addRepository("test-" + i, repositories.get(i));
        }

        MetadataTestUtils.writeChannels(metadataDir.resolve(ProsperoMetadataUtils.INSTALLER_CHANNELS_FILE_NAME),
                List.of(builder.build()));
    }

    public static InstallationMetadata createInstallationMetadata(Path installation) throws MetadataException {
        return createInstallationMetadata(installation, createManifest(null),
                List.of(new Channel("test-channel", "", null,
                        List.of(new Repository("test", "http://test.org")),
                        new ChannelManifestCoordinate("org.test","test"),
                        null, null)));
    }

    public static InstallationMetadata createInstallationMetadata(Path installation, ChannelManifest manifest, List<Channel> channels) throws MetadataException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtils.deleteQuietly(installation.toFile())));
        final InstallationMetadata metadata = InstallationMetadata.newInstallation(installation, manifest,
                new ProsperoConfig(channels), Optional.empty());
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

    public static Path prepareChannel(String manifestDescriptor) throws IOException {
        final Path provisionConfigFile = Files.createTempFile("channels", ".yaml").toAbsolutePath();
        provisionConfigFile.toFile().deleteOnExit();

        prepareChannel(provisionConfigFile, manifestDescriptor);
        return provisionConfigFile;
    }

    public static void prepareChannel(Path channelFile, String... manifestDescriptor)
            throws IOException {
        List<URL> channelUrls = Arrays.stream(manifestDescriptor)
                .map(d->MetadataTestUtils.class.getClassLoader().getResource(d))
                .collect(Collectors.toList());
        prepareChannel(channelFile, channelUrls);
    }

    public static void prepareChannel(Path channelFile, List<Repository> repositories, String... manifests) throws IOException {
        List<URL> channelUrls = Arrays.stream(manifests)
                .map(d->MetadataTestUtils.class.getClassLoader().getResource(d))
                .collect(Collectors.toList());
        prepareChannel(channelFile, channelUrls, repositories);
    }

    public static void prepareChannel(Path channelFile, List<URL> manifestUrls) throws IOException {
        List<Channel> channels = new ArrayList<>();
        List<Repository> repositories = defaultRemoteRepositories().stream()
                .map(r->new Repository(r.getId(), r.getUrl())).collect(Collectors.toList());
        for (int i=0; i<manifestUrls.size(); i++) {
            channels.add(new Channel("test-channel-" + i, "", null, repositories,
                    new ChannelManifestCoordinate(manifestUrls.get(i)),
                    null, Channel.NoStreamStrategy.NONE));
        }

        writeChannels(channelFile, channels);
    }

    public static void prepareChannel(Path channelFile, List<URL> manifestUrls, List<Repository> repositories) throws IOException {
        List<Channel> channels = new ArrayList<>();
        for (int i=0; i<manifestUrls.size(); i++) {
            channels.add(new Channel("test-channel-" + i, "", null, repositories,
                    new ChannelManifestCoordinate(manifestUrls.get(i)),
                    null, Channel.NoStreamStrategy.NONE));
        }

        writeChannels(channelFile, channels);
    }

    public static void writeChannels(Path channelFile, List<Channel> channels) throws IOException {
        Files.writeString(channelFile, ChannelMapper.toYaml(channels));
    }

    public static List<Channel> readChannels(Path channelFile) throws IOException {
        return ChannelMapper.fromString(Files.readString(channelFile));
    }

    public static List<RemoteRepository> defaultRemoteRepositories() {
        return Arrays.asList(
                new RemoteRepository.Builder("maven-central", "default", "https://repo1.maven.org/maven2/").build(),
                new RemoteRepository.Builder("nexus", "default", "https://repository.jboss.org/nexus/content/groups/public").build(),
                new RemoteRepository.Builder("maven-redhat-ga", "default", "https://maven.repository.redhat.com/ga").build()
        );
    }

    public static void copyManifest(String resource, Path targetPath) throws IOException {
        final InputStream resourceStream = MetadataTestUtils.class.getClassLoader().getResourceAsStream(resource);

        try (FileWriter writer = new FileWriter(targetPath.toFile())) {
            IOUtils.copy(resourceStream, writer, StandardCharsets.UTF_8);
        }
    }

    public static void upgradeStreamInManifest(Path manifestPath, Artifact upgrade) throws IOException {
        final ChannelManifest manifest = ChannelManifestMapper.from(manifestPath.toUri().toURL());
        final List<Stream> updatedStreams = manifest.getStreams().stream()
                .map(s -> {
                    if (s.getGroupId().equals(upgrade.getGroupId()) && s.getArtifactId().equals(upgrade.getArtifactId())) {
                        return new Stream(upgrade.getGroupId(), upgrade.getArtifactId(), upgrade.getVersion());
                    } else {
                        return s;
                    }
                })
                .collect(Collectors.toList());
        Files.writeString(manifestPath, ChannelManifestMapper.toYaml(new ChannelManifest(manifest.getName(), manifest.getId(), manifest.getDescription(), updatedStreams)));
    }
}
