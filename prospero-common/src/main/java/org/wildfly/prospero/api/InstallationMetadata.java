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

package org.wildfly.prospero.api;

import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.installation.git.GitStorage;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.wildfly.prospero.model.ProvisioningConfig;
import org.wildfly.prospero.model.RepositoryRef;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.wildfly.channel.Channel;
import org.wildfly.channel.Stream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class InstallationMetadata {

    public static final String METADATA_DIR = ".installation";
    public static final String MANIFEST_FILE_NAME = "manifest.yaml";
    public static final String PROSPERO_CONFIG_FILE_NAME = "prospero-config.yaml";
    public static final String PROVISIONING_FILE_NAME = "provisioning.xml";
    public static final String GALLEON_INSTALLATION_DIR = ".galleon";
    private final Path manifestFile;
    private final Path prosperoConfigFile;
    private final Path provisioningFile;
    private Channel manifest;
    private org.jboss.galleon.config.ProvisioningConfig provisioningConfig;
    private List<ChannelRef> channelRefs;
    private List<RemoteRepository> repositories;
    private final GitStorage gitStorage;
    private Path base;

    private InstallationMetadata(Path manifestFile, Path prosperoConfigFile, Path provisioningFile) throws MetadataException {
        this.base = manifestFile.getParent();
        this.gitStorage = null;
        this.manifestFile = manifestFile;
        this.prosperoConfigFile = prosperoConfigFile;
        this.provisioningFile = provisioningFile;

        doInit(manifestFile, prosperoConfigFile, provisioningFile);
    }

    public InstallationMetadata(Path base) throws MetadataException {
        this(base, new GitStorage(base));
    }

    protected InstallationMetadata(Path base, GitStorage gitStorage) throws MetadataException {
        this.base = base;
        this.gitStorage = gitStorage;
        this.manifestFile = base.resolve(METADATA_DIR).resolve(InstallationMetadata.MANIFEST_FILE_NAME);
        this.prosperoConfigFile = base.resolve(METADATA_DIR).resolve(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME);
        this.provisioningFile = base.resolve(GALLEON_INSTALLATION_DIR).resolve(InstallationMetadata.PROVISIONING_FILE_NAME);

        doInit(manifestFile, prosperoConfigFile, provisioningFile);
    }

    public InstallationMetadata(Path base, Channel channel, List<ChannelRef> channelRefs,
                                List<RemoteRepository> repositories) throws MetadataException {
        this.base = base;
        this.gitStorage = new GitStorage(base);
        this.manifestFile = base.resolve(METADATA_DIR).resolve(InstallationMetadata.MANIFEST_FILE_NAME);
        this.prosperoConfigFile = base.resolve(METADATA_DIR).resolve(InstallationMetadata.PROSPERO_CONFIG_FILE_NAME);
        this.provisioningFile = base.resolve(GALLEON_INSTALLATION_DIR).resolve(InstallationMetadata.PROVISIONING_FILE_NAME);

        this.manifest = channel;
        this.channelRefs = channelRefs;
        this.repositories = repositories;
        try {
            this.provisioningConfig = ProvisioningXmlParser.parse(provisioningFile);
        } catch (ProvisioningException e) {
            throw new MetadataException("Error when parsing installation metadata", e);
        }
    }

    private void doInit(Path manifestFile, Path provisionConfig, Path provisioningFile) throws MetadataException {
        try {
            this.manifest = ManifestYamlSupport.parse(manifestFile.toFile());
            final ProvisioningConfig provisioningConfig = ProvisioningConfig.readConfig(provisionConfig);
            this.channelRefs = provisioningConfig.getChannels();
            this.repositories = provisioningConfig.getRepositories()
                    .stream().map(r -> r.toRemoteRepository()).collect(Collectors.toList());
            this.provisioningConfig = ProvisioningXmlParser.parse(provisioningFile);
        } catch (IOException | ProvisioningException e) {
            throw new MetadataException("Error when parsing installation metadata", e);
        }
    }

    public static InstallationMetadata importMetadata(Path location) throws IOException, MetadataException {
        Path manifestFile = null;
        Path provisionConfigFile = null;
        Path provisioningFile = null;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(location.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {

                if (entry.getName().equals(MANIFEST_FILE_NAME)) {
                    manifestFile = Files.createTempFile("manifest", "yaml");
                    Files.copy(zis, manifestFile, StandardCopyOption.REPLACE_EXISTING);
                    manifestFile.toFile().deleteOnExit();
                }

                if (entry.getName().equals(PROSPERO_CONFIG_FILE_NAME)) {
                    provisionConfigFile = Files.createTempFile("channels", "yaml");
                    Files.copy(zis, provisionConfigFile, StandardCopyOption.REPLACE_EXISTING);
                    provisionConfigFile.toFile().deleteOnExit();
                }

                if (entry.getName().equals(PROVISIONING_FILE_NAME)) {
                    provisioningFile = Files.createTempFile("provisioning", "xml");
                    Files.copy(zis, provisioningFile, StandardCopyOption.REPLACE_EXISTING);
                    provisioningFile.toFile().deleteOnExit();
                }
            }
        }

        if (manifestFile == null || provisionConfigFile == null || provisioningFile == null) {
            throw new IllegalArgumentException("Provided metadata bundle is missing one or more entries");
        }

        return new InstallationMetadata(manifestFile, provisionConfigFile, provisioningFile);
    }

    public Path exportMetadataBundle(Path location) throws IOException {
        final File file = location.toFile();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            zos.putNextEntry(new ZipEntry(MANIFEST_FILE_NAME));
            try(FileInputStream fis = new FileInputStream(manifestFile.toFile())) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(PROSPERO_CONFIG_FILE_NAME));
            try(FileInputStream fis = new FileInputStream(prosperoConfigFile.toFile())) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(PROVISIONING_FILE_NAME));
            try(FileInputStream fis = new FileInputStream(provisioningFile.toFile())) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
        }
        return file.toPath();
    }

    public Channel getManifest() {
        return manifest;
    }

    public org.jboss.galleon.config.ProvisioningConfig getProvisioningConfig() {
        return provisioningConfig;
    }

    public void writeFiles() throws MetadataException {
        try {
            ManifestYamlSupport.write(this.manifest, this.manifestFile);
        } catch (IOException e) {
            throw new MetadataException("Unable to save manifest in installation", e);
        }

        writeProvisioningConfig();

        gitStorage.record();
    }

    private void writeProvisioningConfig() throws MetadataException {
        try {
            final ProvisioningConfig provisioningConfig = new ProvisioningConfig(this.channelRefs,
                    repositories.stream().map(r -> new RepositoryRef(r.getId(), r.getUrl())).collect(Collectors.toList()));
            provisioningConfig.writeConfig(this.prosperoConfigFile.toFile());
        } catch (IOException e) {
            throw new MetadataException("Unable to save channel list in installation", e);
        }
    }

    public List<SavedState> getRevisions() throws MetadataException {
        return gitStorage.getRevisions();
    }

    public InstallationMetadata rollback(SavedState savedState) throws MetadataException {
        // checkout previous version
        // record as rollback operation
        gitStorage.revert(savedState);

        // re-parse metadata
        return new InstallationMetadata(base);
    }

    public List<ArtifactChange> getChangesSince(SavedState savedState) throws MetadataException {
        return gitStorage.getChanges(savedState);
    }

    public void setChannel(Channel resolvedChannel) {
        manifest = resolvedChannel;
    }

    public List<Artifact> getArtifacts() {
        return manifest.getStreams().stream().map(s-> streamToArtifact(s)).collect(Collectors.toList());
    }

    private DefaultArtifact streamToArtifact(Stream s) {
        return new DefaultArtifact(s.getGroupId(), s.getArtifactId(), "jar", s.getVersion());
    }

    public Artifact find(Artifact gav) {
        for (Stream stream : manifest.getStreams()) {
            if (stream.getGroupId().equals(gav.getGroupId()) && stream.getArtifactId().equals(gav.getArtifactId())) {
                return streamToArtifact(stream);
            }
        }
        return null;
    }

    public ProvisioningConfig getProsperoConfig() {
        return new ProvisioningConfig(new ArrayList<>(channelRefs), repositories.stream().map(RepositoryRef::new).collect(Collectors.toList()));
    }

    public void updateProsperoConfig(ProvisioningConfig config) throws MetadataException {
        this.channelRefs = new ArrayList<>(config.getChannels());
        this.repositories = config.getRepositories().stream().map(RepositoryRef::toRemoteRepository).collect(Collectors.toList());

        writeProvisioningConfig();

        gitStorage.recordConfigChange();
    }
}
