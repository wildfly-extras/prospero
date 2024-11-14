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

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.util.HashUtils;
import org.jboss.galleon.util.IoUtils;
import org.jboss.logging.Logger;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.MavenArtifact;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.metadata.ManifestVersionRecord;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Manages artifact cache located in {@code installationDir}/{@code CACHE_FOLDER}.
 *
 * Cached artifacts are listed in {@code CACHE_FOLDER}/{@code CACHE_FILENAME}. Each artifact is identified by its {@code GAV}
 * and specifies an SHA-1 hash of the file and a relative path were the artifact can be found within {@code installationDir}.
 *
 * If the artifact cannot be found within Galleon-provisioned {@code installationDir}, the artifact can be added to the
 * {@code CACHE_FOLDER}.
 *
 * The cache is rebuild during update and only current artifacts are stored.
 */
public class ArtifactCache {
    private static final Logger LOG = Logger.getLogger(ArtifactCache.class);

    static final String CACHE_LINE_SEPARATOR = "::";
    static final String CACHE_FILENAME = "artifacts.txt";
    public static final Path CACHE_FOLDER = Path.of(ProsperoMetadataUtils.METADATA_DIR, ".cache");

    private final Path cacheDir;
    private final Path installationDir;

    private final Map<String, Path> paths = new TreeMap<>();
    private final Map<String, String> hashes = new TreeMap<>();
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

    public static void cleanInstancesCache() {
        synchronized (instances) {
            instances.clear();
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
     *
     * @param artifact - artifact to be recorded
     * @param pathToArtifact - location in the installation where the artifact can be found
     * @throws IOException
     */
    public void record(MavenArtifact artifact, Path pathToArtifact) throws IOException {
        try {
            lock.writeLock().lock();

            final Path cacheList = cacheDir.resolve(CACHE_FILENAME);

            final String hash = HashUtils.hashFile(artifact.getFile().toPath());

            // make sure the latest version of the cache list is read
            init();

            // add the file to the paths/hashes
            paths.put(
                    asKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(), artifact.getVersion()),
                    pathToArtifact
            );
            hashes.put(
                    asKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(), artifact.getClassifier(), artifact.getVersion()),
                    hash
            );

            if (Files.exists(cacheList)) {
                Files.delete(cacheList);
            }

            // write all the paths/hashes to make sure they are in alphabetic order
            try (BufferedWriter writer = Files.newBufferedWriter(cacheList, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
                for (String key: paths.keySet()) {
                    final Path relativePath = installationDir.relativize(paths.get(key));
                    final String recordedPath = relativePath.toString().replace(File.separatorChar, '/');
                    String cacheLine = key + CACHE_LINE_SEPARATOR + hashes.get(key) + CACHE_LINE_SEPARATOR + recordedPath + "\n";
                    writer.write(cacheLine);
                }
            }
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

    /**
     * detects and caches the manifests from {@code manifestRecord} in {@code CACHE_FOLDER}.
     * The version and content of the manifest is resolved using {@code resolvedArtifacts}.
     * NOTE: only manifests identified by maven coordinates are cached.
     *
     * @param manifestRecord - record containing all manifest used in installation.
     * @param localRepositoryManager - Maven manager for the local repository.
     * @throws IOException
     */
    public void cache(ManifestVersionRecord manifestRecord, LocalRepositoryManager localRepositoryManager) throws IOException {
        Objects.requireNonNull(manifestRecord);
        Objects.requireNonNull(localRepositoryManager);

        for (ManifestVersionRecord.MavenManifest manifest : manifestRecord.getMavenManifests()) {
            final MavenArtifact record = mapToFile(manifestRecord, localRepositoryManager, manifest.getGroupId(), manifest.getArtifactId());
            if (record != null && record.getVersion().equals(manifest.getVersion())) {
                if (LOG.isDebugEnabled()) {
                    LOG.debugf("Adding manifest %s to the cache", record);
                }
                final File cachedManifest = record.getFile();

                if (cachedManifest.exists()) {
                    cache(record);
                }
            }
        }
    }

    private MavenArtifact mapToFile(ManifestVersionRecord manifestRecord, LocalRepositoryManager localRepositoryManager,
                                    String groupId, String artifactId) {
        final Optional<String> version = manifestRecord.getMavenManifests().stream()
                .filter(m -> m.getGroupId().equals(groupId) && m.getArtifactId().equals(artifactId))
                .map(ManifestVersionRecord.MavenManifest::getVersion)
                .findFirst();
        return version
                .map(v -> localRepositoryManager.getPathForLocalArtifact(new DefaultArtifact(groupId, artifactId, ChannelManifest.CLASSIFIER, ChannelManifest.EXTENSION, v)))
                .map(p -> localRepositoryManager.getRepository().getBasedir().toPath().resolve(p).toFile())
                .map(f -> new MavenArtifact(groupId, artifactId, ChannelManifest.EXTENSION, ChannelManifest.CLASSIFIER, version.get(), f))
                .orElse(null);
    }

    private void init() throws IOException {
        Path artifactLog = cacheDir.resolve(CACHE_FILENAME);

        if (Files.exists(artifactLog)) {
            int row = 0;
            final List<String> lines = Files.readAllLines(artifactLog);
            try {
                for ( ; row < lines.size(); row++) {
                    final String[] splitLine = lines.get(row).split(CACHE_LINE_SEPARATOR);
                    if (splitLine.length < 3) {
                        throw new IOException("Not enough segments, expected format is <GAV>::<hash>::<path>");
                    }
                    String gav = splitLine[0];
                    String hash = splitLine[1];
                    Path path = Paths.get(splitLine[2]);
                    final org.jboss.galleon.universe.maven.MavenArtifact mavenArtifact = org.jboss.galleon.universe.maven.MavenArtifact.fromString(gav);
                    final String key = asKey(mavenArtifact.getGroupId(), mavenArtifact.getArtifactId(), mavenArtifact.getExtension(), mavenArtifact.getClassifier(), mavenArtifact.getVersion());
                    paths.put(key, installationDir.resolve(path));
                    hashes.put(key, hash);
                }
            } catch (MavenUniverseException | IOException e) {
                throw ProsperoLogger.ROOT_LOGGER.unableToReadArtifactCache(row + 1, lines.get(row), e);
            }
        }
    }

    private static String asKey(String groupId, String artifactId, String extension, String classifier, String version) {
        final StringBuilder buf = new StringBuilder();
        buf.append(groupId).append(':').append(artifactId);
        if (version == null) {
            return buf.toString();
        }
        if (extension != null) {
            buf.append(':').append(extension);
        }
        if (classifier != null && !classifier.isEmpty()) {
            buf.append(':').append(classifier);
        }
        return buf.append(':').append(version).toString();
    }

    public List<CachedArtifact> listArtifacts() {
        final List<CachedArtifact> cacheEntries = new ArrayList<>();

        final Set<String> artifactKeys = paths.keySet();
        for (String artifactKey : artifactKeys) {
            final Path artifactPath = paths.get(artifactKey);
            final String artifactHash = hashes.get(artifactKey);

            final String[] gav = artifactKey.split(":");
            final String groupId = gav[0];
            final String artifactId = gav[1];
            final String extension = gav[2];
            final String classifier;
            final String version;
            if (gav.length == 5) {
                classifier = gav[3];
                version = gav[4];
            } else {
                classifier = null;
                version = gav[3];
            }


            cacheEntries.add(new CachedArtifact(groupId, artifactId, extension, classifier, version, artifactHash, artifactPath));
        }
        return cacheEntries;
    }

    public static class CachedArtifact {
        private final String groupId;
        private final String artifactId;
        private final String version;
        private final String classifier;
        private final String extension;
        private final Path path;
        private final String hash;

        CachedArtifact(String groupId, String artifactId, String extension, String classifier, String version, String hash, Path path) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.extension = extension;
            this.classifier = classifier;
            this.version = version;
            this.hash = hash;
            this.path = path;
        }

        public String getGav() {
            return ArtifactCache.asKey(groupId, artifactId, extension, classifier, version);
        }

        public Path getPath() {
            return path;
        }

        public String getHash() {
            return hash;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        public String getExtension() {
            return extension;
        }

        public String getClassifier() {
            return classifier;
        }

        @Override
        public String toString() {
            return "CachedArtifact{" +
                    "groupId='" + groupId + '\'' +
                    ", artifactId='" + artifactId + '\'' +
                    ", version='" + version + '\'' +
                    ", classifier='" + classifier + '\'' +
                    ", extension='" + extension + '\'' +
                    ", path=" + path +
                    ", hash='" + hash + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CachedArtifact that = (CachedArtifact) o;
            return Objects.equals(groupId, that.groupId) && Objects.equals(artifactId, that.artifactId) && Objects.equals(version, that.version) && Objects.equals(classifier, that.classifier) && Objects.equals(extension, that.extension) && Objects.equals(path, that.path) && Objects.equals(hash, that.hash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, artifactId, version, classifier, extension, path, hash);
        }
    }
}
