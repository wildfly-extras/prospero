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

package org.wildfly.prospero.galleon;

import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.util.HashUtils;
import org.jboss.galleon.util.IoUtils;
import org.jboss.logging.Logger;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.prospero.api.InstallationMetadata;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ArtifactCache {
    private static final Logger LOG = Logger.getLogger(ArtifactCache.class);

    static final String CACHE_LINE_SEPARATOR = "::";
    static final String CACHE_FILENAME = "artifacts.txt";
    public static final Path CACHE_FOLDER = Path.of(InstallationMetadata.METADATA_DIR, ".cache");

    private final Path cacheDir;
    private final Path installationDir;

    private final Map<String, Path> paths = new HashMap<>();
    private final Map<String, String> hashes = new HashMap<>();
    private ReadWriteLock lock = new ReentrantReadWriteLock();

    private static final HashMap<Path, ArtifactCache> instances = new HashMap<>();

    /**
     * returns artifact cache located at {@code installationDir}/{code CACHE_FOLDER}
     *
     * @param installationDir
     * @return
     * @throws IOException if the cache descriptor is corrupt and cannot be read
     */
    public static ArtifactCache getInstance(Path installationDir) throws IOException {
        synchronized (instances) {
            if (!instances.containsKey(installationDir.toAbsolutePath())) {
                instances.put(installationDir.toAbsolutePath(), new ArtifactCache(installationDir));
            }
            return instances.get(installationDir.toAbsolutePath());
        }
    }

    private ArtifactCache(Path installationDir) throws IOException {
        this.installationDir = installationDir;
        this.cacheDir = installationDir.resolve(CACHE_FOLDER);

        init();
    }

    /**
     * finds a file associated with the {@code GAV} in the local {@code installationDir}. The file is only matched
     * if it's {@code GAV} is recorded in cache descriptor and the file has not been modified since the cache was created.
     *
     * @param groupId
     * @param artifactId
     * @param extension
     * @param classifier
     * @param version
     * @return empty {@code Optional} if the file has not been matched, otherwise the matching {@code File}
     */
    public Optional<File> getArtifact(String groupId, String artifactId, String extension, String classifier, String version) {
        final String key = asKey(groupId, artifactId, extension, classifier, version);
        try {
            lock.readLock().lock();
            if (paths.containsKey(key)) {
                final Path path = paths.get(key);
                try {
                    final String hash = HashUtils.hashFile(path);
                    if (!hash.equals(hashes.get(key))) {
                        LOG.debug("Hashes don't match for " + key);
                        return Optional.empty();
                    }
                    return Optional.of(path.toFile());
                } catch (IOException e) {
                    LOG.debug("Unable to calculate cached artifact hash " + key, e);
                    return Optional.empty();
                }
            }
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * records file in the cache descriptor. The recorded path is relative to {@code installationDir}
     * @param artifact - artifact to be recorded
     * @param pathToArtifact - location in the installation where the artifact can be found
     * @throws IOException
     */
    public void record(MavenArtifact artifact, Path pathToArtifact) throws IOException {
        try {
            lock.writeLock().lock();

            final String cacheFileKey = getCacheFileKey(artifact);

            final Path cacheList = cacheDir.resolve(CACHE_FILENAME);

            if (paths.containsKey(asKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(), artifact.getVersion()))) {
                removeArtifactFromCacheList(cacheFileKey, cacheList);
            }

            final String hash = HashUtils.hashFile(artifact.getFile().toPath());
            Files.writeString(cacheList,
                    cacheFileKey + CACHE_LINE_SEPARATOR + hash + CACHE_LINE_SEPARATOR +
                            installationDir.relativize(pathToArtifact) + System.lineSeparator(), StandardOpenOption.APPEND, StandardOpenOption.CREATE);

            invalidate();
            init();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * caches the artifact in {@code CACHE_FOLDER}. The cached artifact is then recorded in the cache list.
     *
     * @param artifact
     * @throws IOException
     */
    public void cache(MavenArtifact artifact) throws IOException {
        IoUtils.copy(artifact.getFile().toPath(), cacheDir.resolve(artifact.getFile().getName()), false);

        record(artifact, cacheDir.resolve(artifact.getFile().getName()));
    }

    private static String getCacheFileKey(MavenArtifact artifact) {
        final org.jboss.galleon.universe.maven.MavenArtifact galleonArtifact = new org.jboss.galleon.universe.maven.MavenArtifact();
        galleonArtifact.setGroupId(artifact.getGroupId());
        galleonArtifact.setArtifactId(artifact.getArtifactId());
        galleonArtifact.setClassifier(artifact.getClassifier());
        galleonArtifact.setExtension(artifact.getExtension());
        galleonArtifact.setVersion(artifact.getVersion());
        return galleonArtifact.getCoordsAsString();
    }

    private static void removeArtifactFromCacheList(String cacheKey, Path cacheList) throws IOException {
        final List<String> cacheLines = Files.readAllLines(cacheList);
        Files.delete(cacheList);

        try (final BufferedWriter writer = Files.newBufferedWriter(cacheList, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            for (String cacheLine : cacheLines) {
                if (!cacheLine.split(CACHE_LINE_SEPARATOR)[0].equals(cacheKey)) {
                    writer.write(cacheLine + System.lineSeparator());
                }
            }
        }
    }

    private void init() throws IOException {
        Path artifactLog = cacheDir.resolve(ArtifactCache.CACHE_FILENAME);

        if (Files.exists(artifactLog)) {
            try {
                final List<String> lines = Files.readAllLines(artifactLog);
                for (String line : lines) {
                    String gav = line.split(ArtifactCache.CACHE_LINE_SEPARATOR)[0];
                    String hash = line.split(ArtifactCache.CACHE_LINE_SEPARATOR)[1];
                    Path path = Paths.get(line.split(ArtifactCache.CACHE_LINE_SEPARATOR)[2]);
                    final org.jboss.galleon.universe.maven.MavenArtifact mavenArtifact = org.jboss.galleon.universe.maven.MavenArtifact.fromString(gav);
                    final String key = asKey(mavenArtifact.getGroupId(), mavenArtifact.getArtifactId(), mavenArtifact.getExtension(), mavenArtifact.getClassifier(), mavenArtifact.getVersion());
                    paths.put(key, installationDir.resolve(path));
                    hashes.put(key, hash);
                }
            } catch (MavenUniverseException e) {
                throw new IOException("Unable to read cached items.", e);
            }
        }
    }

    private void invalidate() {
        paths.clear();
        hashes.clear();
    }

    private static String asKey(String groupId, String artifactId, String extension, String classifier, String version) {
        return String.format("%s:%s:%s:%s:%s", groupId, artifactId, version, classifier, extension);
    }
}
