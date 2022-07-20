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

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.wildfly.channel.ArtifactCoordinate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class PatchArchive implements AutoCloseable {

    public static final String PATCH_REPO_FOLDER = "repository";
    public static final String FS = "/";
    private final Path extracted;
    private List<ArtifactCoordinate> artifactCoordinates;

    private PatchArchive(Path extracted) throws IOException {
        this.extracted = extracted;
        this.artifactCoordinates = ArtifactList.readFrom(extracted.resolve("artifact-list.yaml")).getArtifactCoordinates();
    }

    public List<ArtifactCoordinate> getArtifactList() {
        return artifactCoordinates;
    }


    public Path getRepository() {
        return extracted.resolve(PATCH_REPO_FOLDER);
    }

    @Override
    public void close() {
        FileUtils.deleteQuietly(extracted.toFile());
    }

    public static PatchArchive extract(Path patchArchive) throws IOException {
        Path extracted = null;
        try {
            // TODO: validate content??

            return new PatchArchive(unzipArchive(patchArchive.toFile()));
        } finally {
            if (extracted != null) {
                FileUtils.deleteQuietly(extracted.toFile());
            }
        }
    }

    public static Path createPatchArchive(List<? extends Artifact> artifacts, File archive) throws IOException {
        Objects.requireNonNull(artifacts);
        Objects.requireNonNull(archive);

        if (artifacts.isEmpty()) {
            throw new IllegalArgumentException("Cannot create bundle without artifacts.");
        }

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(archive))) {
            zos.putNextEntry(new ZipEntry("artifact-list.yaml"));
            final ArtifactList artifactList = new ArtifactList(artifacts.stream().map(a->org.wildfly.prospero.patch.Artifact.from(a)).collect(Collectors.toList()));
            final String listYaml = artifactList.writeToString();
            zos.write(listYaml.getBytes(StandardCharsets.UTF_8), 0, listYaml.length());

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
                String fileName = entry;
                zos.putNextEntry(new ZipEntry(fileName));

                try(FileInputStream fis = new FileInputStream(artifact.getFile())) {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }

                entry = fileName + ".md5";
                zos.putNextEntry(new ZipEntry(entry));
                final String md5 = DigestUtils.md5Hex(new FileInputStream(artifact.getFile()));
                zos.write(md5.getBytes(), 0, md5.getBytes().length);

                entry = fileName + ".sha1";
                zos.putNextEntry(new ZipEntry(entry));
                final String sha1 = DigestUtils.sha1Hex(new FileInputStream(artifact.getFile()));
                zos.write(sha1.getBytes(), 0, sha1.getBytes().length);
            }
        }

        return archive.toPath();
    }

    private static Path unzipArchive(File patchArchive) throws IOException {
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
