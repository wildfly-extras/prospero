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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.aether.artifact.Artifact;
import org.wildfly.channel.Channel;
import org.wildfly.channel.ChannelMapper;
import org.wildfly.channel.Stream;
import org.wildfly.prospero.actions.ApplyPatch;
import org.wildfly.prospero.api.exceptions.MetadataException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class PatchArchive {

    public static final String CHANNEL_SUFFIX = "-channel.yaml";
    public static final String PATCH_REPO_FOLDER = "repository";
    public static final String FS = "/";
    private final Path patchArchive;

    public PatchArchive(Path patchArchive) {
        this.patchArchive = patchArchive;
    }

    public String getName() {
        // TODO: the patch name should be based on the channel name not archive
        final String archiveName = patchArchive.getFileName().toString();
        return archiveName.substring(0, archiveName.lastIndexOf('.'));
    }

    /**
     *
     * @param server
     * @return path of extracted patch channel definition
     * @throws IOException
     * @throws MetadataException
     */
    public Patch extract(Path server) throws IOException, MetadataException {
        Path extracted = null;
        try {
            extracted = unzipArchive(patchArchive.toFile());

            // TODO: validate??

            if (!Files.exists(server.resolve(ApplyPatch.PATCHES_FOLDER))) {
                Files.createDirectory(server.resolve(ApplyPatch.PATCHES_FOLDER));
            }

            final Path cachedPatchFile = cachePatchChannel(server, extracted);

            cacheRepositoryContent(server, extracted);

            return new Patch(server, cachedPatchFile.getFileName().toString());
        } finally {
            if (extracted != null) {
                FileUtils.deleteQuietly(extracted.toFile());
            }
        }
    }

    public static Path createPatchArchive(List<Artifact> artifacts, File archive, String patchName) throws Exception {
        Channel channel = new Channel(patchName, null, null, null,
                artifacts.stream().map(a-> new Stream(a.getGroupId(), a.getArtifactId(), a.getVersion())).collect(Collectors.toList()));

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(archive))) {
            zos.putNextEntry(new ZipEntry(patchName + CHANNEL_SUFFIX));
            String channelStr = ChannelMapper.toYaml(channel);
            zos.write(channelStr.getBytes(StandardCharsets.UTF_8), 0, channelStr.length());


            zos.putNextEntry(new ZipEntry(PATCH_REPO_FOLDER + FS));
            for (Artifact artifact : artifacts) {
                String entry = PATCH_REPO_FOLDER + FS;
                for (String dir : artifact.getGroupId().split("\\.")) {
                    entry += dir + FS;
                    zos.putNextEntry(new ZipEntry(entry));
                }
                entry += artifact.getArtifactId() + FS;
                zos.putNextEntry(new ZipEntry(entry));
                entry += artifact.getVersion() + FS;
                zos.putNextEntry(new ZipEntry(entry));
                entry += artifact.getFile().getName();
                zos.putNextEntry(new ZipEntry(entry));

                try(FileInputStream fis = new FileInputStream(artifact.getFile())) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
            }
        }

        return archive.toPath();
    }

    private void cacheRepositoryContent(Path server, Path extracted) throws IOException {
        final Path extractedRepository = extracted.resolve(PATCH_REPO_FOLDER);
        Files.walkFileTree(extractedRepository, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                final Path targetDir = server.resolve(ApplyPatch.PATCHES_FOLDER).resolve(extracted.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.copy(dir, targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                final Path targetFile = server.resolve(ApplyPatch.PATCHES_FOLDER).resolve(extracted.relativize(file));
                if (!Files.exists(targetFile)) {
                    Files.copy(file, targetFile);
                } else if (!IOUtils.contentEquals(new FileReader(file.toFile()), new FileReader(targetFile.toFile()))) {
                    throw new IllegalArgumentException(String.format("File %s differs from existing artifact with the same name", file));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Path cachePatchChannel(Path server, Path extracted) throws IOException {
        final List<Path> patchFiles = Files.list(extracted).filter(p -> p.getFileName().toString().endsWith(CHANNEL_SUFFIX)).collect(Collectors.toList());
        if (patchFiles.size() != 1) {
            throw new IllegalArgumentException("The patch archive can have only a single channel file");
        }
        final Path patchFile = patchFiles.get(0);
        final Path cachedPatchFile = server.resolve(ApplyPatch.PATCHES_FOLDER).resolve(patchFile.getFileName());
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
