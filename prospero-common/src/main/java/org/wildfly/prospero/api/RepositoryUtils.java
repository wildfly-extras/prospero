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

import org.apache.commons.io.IOUtils;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.util.ZipUtils;
import org.jboss.logging.Logger;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.api.exceptions.InvalidRepositoryArchiveException;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.wildfly.channel.maven.VersionResolverFactory.DEFAULT_REPOSITORY_POLICY;

public class RepositoryUtils {
    private static final Logger LOG = Logger.getLogger(RepositoryUtils.class.getName());
    public static Repository toChannelRepository(RemoteRepository r) {
        return new Repository(r.getId(), r.getUrl());
    }

    public static RemoteRepository toRemoteRepository(String id, String url) {
        return new RemoteRepository.Builder(id, "default", url)
                .setPolicy(DEFAULT_REPOSITORY_POLICY)
                .build();
    }

    public static RemoteRepository toRemoteRepository(Repository repository) {
        return toRemoteRepository(repository.getId(), repository.getUrl());
    }

    /**
     * extracts repositories provided as ZIP archives and produces a list of {@code Repositories} pointing to extracted folders.
     *
     * @param repositories - list of repositories. Some of them might contain archives
     * @param temporaryFiles - {@link TemporaryFilesManager} responsible for temporary files
     * @return - list of repositories with extracted archives
     * @throws InvalidRepositoryArchiveException - if the archive does not contain a valid repository.
     */
    public static List<Repository> unzipArchives(List<Repository> repositories, TemporaryFilesManager temporaryFiles) throws InvalidRepositoryArchiveException {
        Objects.requireNonNull(repositories);

        if (repositories.isEmpty()) {
            return repositories;
        }

        final ArrayList<Repository> mappedRepositories = new ArrayList<>();
        for (Repository repository : repositories) {
            try {
                if (isLocalZipFile(repository)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Treating " + repository.getUrl() + " as a local archive.");
                    }
                    final Path archivePath = Path.of(URI.create(repository.getUrl()));
                    final String newUrl = extractArchive(archivePath, temporaryFiles);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Extracted " + repository.getUrl() + " to " + newUrl);
                    }
                    mappedRepositories.add(new Repository(repository.getId(), newUrl));
                } else if (isRemoteZipFile(repository)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Treating " + repository.getUrl() + " as a remote archive.");
                    }
                    final Path archivePath = temporaryFiles.createTempFile("prospero-repository", ".zip");
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Downloaded " + repository.getUrl() + " to " + archivePath);
                    }
                    IOUtils.copy(new URL(repository.getUrl()), archivePath.toFile());

                    final String newUrl = extractArchive(archivePath, temporaryFiles);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Extracted " + repository.getUrl() + " to " + newUrl);
                    }
                    mappedRepositories.add(new Repository(repository.getId(), newUrl));
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Treating " + repository.getUrl() + " as a repository URL.");
                    }
                    mappedRepositories.add(repository);
                }
            } catch (IOException e) {
                throw ProsperoLogger.ROOT_LOGGER.unableToExtractRepositoryArchive(repository.getUrl(), e);
            }
        }
        return mappedRepositories;
    }

    private static String extractArchive(Path archivePath, TemporaryFilesManager temporaryFiles) throws IOException, InvalidRepositoryArchiveException {
        final Path tempRepo = temporaryFiles.createTempDirectory("prospero-repository");
        ZipUtils.unzip(archivePath, tempRepo);

        final Path mavenRepositoryFolder = findRepositoryFolder(tempRepo);
        return mavenRepositoryFolder.toUri().toURL().toString();
    }

    private static Path findRepositoryFolder(Path tempRepo) throws InvalidRepositoryArchiveException {
        final File[] repoChildren = tempRepo.toFile().listFiles(File::isDirectory);
        if (repoChildren == null || repoChildren.length != 1) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("The repository archive has to contain a single root folder. " + tempRepo);
            }
            throw ProsperoLogger.ROOT_LOGGER.invalidRepositoryArchive();
        }
        File rootFile = repoChildren[0];
        final Path mavenRepositoryFolder = rootFile.toPath().resolve("maven-repository");
        if (!Files.exists(mavenRepositoryFolder) || !Files.isDirectory(mavenRepositoryFolder)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Sub-folder maven-repository not found under the root of the archive " + mavenRepositoryFolder.getParent());
            }
            throw ProsperoLogger.ROOT_LOGGER.invalidRepositoryArchive();
        }
        return mavenRepositoryFolder;
    }

    private static boolean isLocalZipFile(Repository repository) {
        try {
            final URI uri = URI.create(repository.getUrl());
            if (!uri.getPath().endsWith(".zip") || !"file".equals(uri.getScheme())) {
                return false;
            }
            final Path path = Path.of(uri);
            // try to open the archive
            FileSystems.newFileSystem(path, (ClassLoader) null).close();
            return true;
        } catch (IOException e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace(repository.getUrl() + " is not a valid zip archive, treating it as URL", e);
            }
            return false;
        }
    }

    private static boolean isRemoteZipFile(Repository repository) {
        try {
            final URL url = new URL(repository.getUrl());
            if (!url.getFile().endsWith(".zip")) {
                return false;
            }
            // check if the content type indicates an archive
            if (url.getProtocol().equals("http") || url.getProtocol().equals("https")) {
                final URLConnection connection = url.openConnection();
                if (connection instanceof HttpURLConnection) {
                    ((HttpURLConnection) connection).setRequestMethod("HEAD");
                    connection.connect();
                    final String contentType = connection.getContentType();
                    return "application/zip".equals(contentType);
                }
            }
        } catch (IOException e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace(repository.getUrl() + " unable to determine content type, treating as repository URL", e);
            }
        }
        return false;
    }
}
