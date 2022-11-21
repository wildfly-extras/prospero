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

package org.wildfly.prospero.api;

import org.apache.commons.lang3.StringUtils;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.installation.git.GitStorage;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.model.ManifestYamlSupport;
import org.wildfly.prospero.model.ProsperoConfig;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.xml.ProvisioningXmlParser;
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

public class InstallationMetadata implements AutoCloseable {

    @Deprecated
    public static final String METADATA_DIR = ProsperoMetadataUtils.METADATA_DIR;
    @Deprecated
    public static final String MANIFEST_FILE_NAME = ProsperoMetadataUtils.MANIFEST_FILE_NAME;
    @Deprecated
    public static final String INSTALLER_CHANNELS_FILE_NAME = ProsperoMetadataUtils.INSTALLER_CHANNELS_FILE_NAME;
    public static final String PROVISIONING_FILE_NAME = "provisioning.xml";
    public static final String GALLEON_INSTALLATION_DIR = ".galleon";
    private final Path manifestFile;
    private final Path channelsFile;
    private final Path provisioningFile;
    private ChannelManifest manifest;
    private org.jboss.galleon.config.ProvisioningConfig galleonProvisioningConfig;
    private List<Channel> channels;
    private final GitStorage gitStorage;
    private final Path base;

    private InstallationMetadata(Path manifestFile, Path channelsFile, Path provisioningFile) throws MetadataException {
        this.base = manifestFile.getParent();
        this.gitStorage = null;
        this.manifestFile = manifestFile;
        this.channelsFile = channelsFile;
        this.provisioningFile = provisioningFile;

        doInit(manifestFile, channelsFile, provisioningFile);
    }

    public InstallationMetadata(Path base) throws MetadataException {
        this(base, new GitStorage(base));
    }

    /*
     * Used in tests only
     */
    protected InstallationMetadata(Path base, GitStorage gitStorage) throws MetadataException {
        this.base = base;
        this.gitStorage = gitStorage;
        this.manifestFile = base.resolve(METADATA_DIR).resolve(InstallationMetadata.MANIFEST_FILE_NAME);
        this.channelsFile = base.resolve(METADATA_DIR).resolve(InstallationMetadata.INSTALLER_CHANNELS_FILE_NAME);
        this.provisioningFile = base.resolve(GALLEON_INSTALLATION_DIR).resolve(InstallationMetadata.PROVISIONING_FILE_NAME);

        doInit(manifestFile, channelsFile, provisioningFile);
    }

    public InstallationMetadata(Path base, ChannelManifest manifest, List<Channel> channels) throws MetadataException {
        this.base = base;
        this.gitStorage = new GitStorage(base);
        this.manifestFile = base.resolve(METADATA_DIR).resolve(InstallationMetadata.MANIFEST_FILE_NAME);
        this.channelsFile = base.resolve(METADATA_DIR).resolve(InstallationMetadata.INSTALLER_CHANNELS_FILE_NAME);
        this.provisioningFile = base.resolve(GALLEON_INSTALLATION_DIR).resolve(InstallationMetadata.PROVISIONING_FILE_NAME);

        this.manifest = manifest;
        this.channels = channels;

        if (channels != null && channels.stream().filter(c-> StringUtils.isEmpty(c.getName())).findAny().isPresent()) {
            throw Messages.MESSAGES.emptyChannelName();
        }

        try {
            this.galleonProvisioningConfig = ProvisioningXmlParser.parse(provisioningFile);
        } catch (ProvisioningException e) {
            throw Messages.MESSAGES.unableToParseConfiguration(provisioningFile, e);
        }
    }

    private void doInit(Path manifestFile, Path channelsFile, Path provisioningFile) throws MetadataException {
        try {
            this.manifest = ManifestYamlSupport.parse(manifestFile.toFile());
        } catch (IOException e) {
            throw Messages.MESSAGES.unableToParseConfiguration(manifestFile, e);
        }
        try {
            this.channels = ProsperoConfig.readConfig(channelsFile).getChannels();
        } catch (MetadataException e) {
            // re-wrap the exception to change the description
            throw Messages.MESSAGES.unableToParseConfiguration(channelsFile, e.getCause());
        }
        try {
            this.galleonProvisioningConfig = ProvisioningXmlParser.parse(provisioningFile);
        } catch (ProvisioningException e) {
            throw Messages.MESSAGES.unableToParseConfiguration(provisioningFile, e);
        }

        try {
            if (gitStorage != null && !gitStorage.isStarted()) {
                gitStorage.record();
            }
        } catch (IOException e) {
            throw Messages.MESSAGES.unableToCreateHistoryStorage(base.resolve(METADATA_DIR), e);
        }
    }

    public static InstallationMetadata importMetadata(Path location) throws IOException, MetadataException {
        Path manifestFile = null;
        Path channelsFile = null;
        Path provisioningFile = null;

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(location.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {

                if (entry.getName().equals(MANIFEST_FILE_NAME)) {
                    manifestFile = Files.createTempFile("manifest", "yaml");
                    Files.copy(zis, manifestFile, StandardCopyOption.REPLACE_EXISTING);
                    manifestFile.toFile().deleteOnExit();
                }

                if (entry.getName().equals(INSTALLER_CHANNELS_FILE_NAME)) {
                    channelsFile = Files.createTempFile("channels", "yaml");
                    Files.copy(zis, channelsFile, StandardCopyOption.REPLACE_EXISTING);
                    channelsFile.toFile().deleteOnExit();
                }

                if (entry.getName().equals(PROVISIONING_FILE_NAME)) {
                    provisioningFile = Files.createTempFile("provisioning", "xml");
                    Files.copy(zis, provisioningFile, StandardCopyOption.REPLACE_EXISTING);
                    provisioningFile.toFile().deleteOnExit();
                }
            }
        }

        if (manifestFile == null || channelsFile == null || provisioningFile == null) {
            throw Messages.MESSAGES.incompleteMetadataBundle(location);
        }

        return new InstallationMetadata(manifestFile, channelsFile, provisioningFile);
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

            zos.putNextEntry(new ZipEntry(INSTALLER_CHANNELS_FILE_NAME));
            try(FileInputStream fis = new FileInputStream(channelsFile.toFile())) {
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

    public ChannelManifest getManifest() {
        return manifest;
    }

    public org.jboss.galleon.config.ProvisioningConfig getGalleonProvisioningConfig() {
        return galleonProvisioningConfig;
    }

    public void recordProvision(boolean overrideProsperoConfig) throws MetadataException {
        recordProvision(overrideProsperoConfig, true);
    }

    public void recordProvision(boolean overrideProsperoConfig, boolean gitRecord) throws MetadataException {
        try {
            ManifestYamlSupport.write(this.manifest, this.manifestFile);
        } catch (IOException e) {
            throw Messages.MESSAGES.unableToSaveConfiguration(manifestFile, e);
        }

        if (overrideProsperoConfig || !Files.exists(this.channelsFile)) {
            writeProsperoConfig();
        }
        if (gitRecord) {
            gitStorage.record();
        }
    }

    private void writeProsperoConfig() throws MetadataException {
        try {
            getProsperoConfig().writeConfig(this.channelsFile);
        } catch (IOException e) {
            throw Messages.MESSAGES.unableToSaveConfiguration(channelsFile, e);
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

    public InstallationChanges getChangesSince(SavedState savedState) throws MetadataException {
        return new InstallationChanges(gitStorage.getArtifactChanges(savedState), gitStorage.getChannelChanges(savedState));
    }

    public void setManifest(ChannelManifest resolvedChannel) {
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

    public ProsperoConfig getProsperoConfig() {
        return new ProsperoConfig(new ArrayList<>(channels));
    }

    public void updateProsperoConfig(ProsperoConfig config) throws MetadataException {
        this.channels = config.getChannels();

        writeProsperoConfig();

        gitStorage.recordConfigChange();
    }

    @Override
    public void close() {
        if (gitStorage != null) {
            try {
                gitStorage.close();
            } catch (Exception e) {
                // log and ignore
                Messages.MESSAGES.unableToCloseStore(e);
            }
        }
    }
}
