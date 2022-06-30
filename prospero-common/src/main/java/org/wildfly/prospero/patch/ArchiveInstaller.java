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

package org.wildfly.prospero.patch;

import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.model.ChannelRef;
import org.wildfly.prospero.model.ProvisioningConfig;
import org.wildfly.prospero.model.RepositoryRef;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ArchiveInstaller {

    private InstallationMetadata metadata;

    public ArchiveInstaller(InstallationMetadata metadata) {
        Objects.requireNonNull(metadata);
        this.metadata = metadata;
    }

    public URL install(File patchArchive, Path server) throws IOException, MetadataException {
        final Path extracted = unzipArchive(patchArchive);

        // TODO: validate??

        if (!Files.exists(server.resolve(".patches"))) {
            Files.createDirectory(server.resolve(".patches"));
        }

        final Path cachedPatchFile = cachePatchChannel(server, extracted);

        cacheRepositoryContent(server, extracted);

        // add patch channel to config
        final ProvisioningConfig provisioningConfig = metadata.getProsperoConfig();
        provisioningConfig.addChannel(new ChannelRef(null, cachedPatchFile.toUri().toURL().toString()));
        // add cached repository to config if not present
        provisioningConfig.addRepository(new RepositoryRef("patch-cache", server.resolve(".patches").resolve("repository").toUri().toURL().toString()));
        metadata.updateProsperoConfig(provisioningConfig);

        return server.resolve(".patches").resolve(cachedPatchFile.getFileName()).toUri().toURL();
    }

    private void cacheRepositoryContent(Path server, Path extracted) throws IOException {
        final Path extractedRepository = extracted.resolve("repository");
        Files.walkFileTree(extractedRepository, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.copy(dir, server.resolve(".patches").resolve(extracted.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, server.resolve(".patches").resolve(extracted.relativize(file)));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Path cachePatchChannel(Path server, Path extracted) throws IOException {
        final List<Path> patchFiles = Files.list(extracted).filter(p -> p.getFileName().toString().endsWith("-channel.yaml")).collect(Collectors.toList());
        if (patchFiles.size() != 1) {
            throw new IllegalArgumentException("The patch archive can have only a single channel file");
        }
        final Path patchFile = patchFiles.get(0);
        final Path cachedPatchFile = server.resolve(".patches").resolve(patchFile.getFileName());
        Files.copy(patchFile, cachedPatchFile);
        return cachedPatchFile;
    }

    private Path unzipArchive(File patchArchive) throws IOException {
        final Path extracted = Files.createTempDirectory("patch");
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(patchArchive))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Files.createDirectories(extracted.resolve(entry.getName()));
                } else {
                    Files.copy(zis, extracted.resolve(entry.getName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return extracted;
    }
}
