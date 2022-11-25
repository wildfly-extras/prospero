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
package org.wildfly.prospero.actions;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import org.jboss.galleon.Constants;
import org.jboss.galleon.Errors;

import org.wildfly.channel.Channel;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.api.TemporaryRepositoriesHandler;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.ArtifactResolutionException;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.model.ProsperoConfig;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.diff.FsDiff;
import static org.jboss.galleon.diff.FsDiff.ADDED;
import static org.jboss.galleon.diff.FsDiff.CONFLICT;
import static org.jboss.galleon.diff.FsDiff.CONFLICTS_WITH_THE_UPDATED_VERSION;
import static org.jboss.galleon.diff.FsDiff.FORCED;
import static org.jboss.galleon.diff.FsDiff.HAS_BEEN_REMOVED_FROM_THE_UPDATED_VERSION;
import static org.jboss.galleon.diff.FsDiff.HAS_CHANGED_IN_THE_UPDATED_VERSION;
import static org.jboss.galleon.diff.FsDiff.MODIFIED;
import static org.jboss.galleon.diff.FsDiff.REMOVED;
import static org.jboss.galleon.diff.FsDiff.formatMessage;
import org.jboss.galleon.diff.FsEntry;
import org.jboss.galleon.layout.SystemPaths;
import org.jboss.galleon.util.HashUtils;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.PathsUtils;
import org.jboss.logging.Logger;
import org.wildfly.prospero.Messages;
import org.wildfly.prospero.installation.git.GitStorage;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;

public class ApplyUpdateAction implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(ApplyUpdateAction.class);

    private final InstallationMetadata installationMetadata;
    private final GalleonEnvironment galleonEnv;
    private final ProsperoConfig prosperoConfig;
    private final Path updateDir;
    private final Path installationDir;
    private final SystemPaths systemPaths;

    public ApplyUpdateAction(Path installationDir, Path updateDir, MavenSessionManager mavenSessionManager, Console console, List<Repository> overrideRepositories)
            throws ProvisioningException, OperationException {
        this.updateDir = updateDir;
        this.installationDir = installationDir;
        this.installationMetadata = new InstallationMetadata(installationDir);

        this.prosperoConfig = addTemporaryRepositories(overrideRepositories);
        try {
            this.systemPaths = SystemPaths.load(updateDir);
        } catch (IOException ex) {
            throw new ProvisioningException(ex);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("System paths " + this.systemPaths.getPaths());
        }
        galleonEnv = GalleonEnvironment
                .builder(installationDir, prosperoConfig, mavenSessionManager)
                .setConsole(console)
                .build();
    }

    public void applyUpdate() throws ProvisioningException, MetadataException, ArtifactResolutionException {
        FsDiff diffs = findChanges();
        try {
            doApplyUpdate(diffs);
            updateMetadata();
        } catch (IOException ex) {
            throw new ProvisioningException(ex);
        }
    }

    public FsDiff findChanges() throws ArtifactResolutionException, ProvisioningException {
        return galleonEnv.getProvisioningManager().getFsDiff();
    }

    @Override
    public void close() {
        installationMetadata.close();
    }

    private ProsperoConfig addTemporaryRepositories(List<Repository> repositories) {
        final ProsperoConfig prosperoConfig = installationMetadata.getProsperoConfig();

        final List<Channel> channels = TemporaryRepositoriesHandler.addRepositories(prosperoConfig.getChannels(), repositories);

        return new ProsperoConfig(channels);
    }

    private void updateMetadata() throws ProvisioningException, ArtifactResolutionException {
        try {
            writeProsperoMetadata();
            Path installationGalleonPath = PathsUtils.getProvisionedStateDir(installationDir);
            Path updateGalleonPath = PathsUtils.getProvisionedStateDir(updateDir);
            IoUtils.recursiveDelete(installationGalleonPath);
            IoUtils.copy(updateGalleonPath, installationGalleonPath, true);
        } catch (IOException | MetadataException ex) {
            throw new ProvisioningException(ex);
        }
    }

    private void writeProsperoMetadata() throws MetadataException, IOException {
        Path updateMetadataDir = updateDir.resolve(ProsperoMetadataUtils.METADATA_DIR);
        Path updateManifest = updateMetadataDir.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME);
        Path updateConf = updateMetadataDir.resolve(ProsperoMetadataUtils.PROSPERO_CONFIG_FILE_NAME);

        Path installationMetadataDir = installationDir.resolve(ProsperoMetadataUtils.METADATA_DIR);
        Path installationManifest = installationMetadataDir.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME);
        Path installationConf = installationMetadataDir.resolve(ProsperoMetadataUtils.PROSPERO_CONFIG_FILE_NAME);
        IoUtils.copy(updateManifest, installationManifest);
        IoUtils.copy(updateConf, installationConf);

        GitStorage git = new GitStorage(installationDir);
        try {
            git.record();

            IoUtils.copy(updateManifest, installationManifest);
            IoUtils.copy(updateConf, installationConf);
        } finally {
            try {
                git.close();
            } catch (Exception e) {
                // log and ignore
                Messages.MESSAGES.unableToCloseStore(e);
            }
        }
    }

    private void handleRemovedFiles(FsDiff fsDiff) throws IOException {
        if (fsDiff.hasRemovedEntries()) {
            for (FsEntry removed : fsDiff.getRemovedEntries()) {
                final Path target = updateDir.resolve(removed.getRelativePath());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(formatMessage(REMOVED, removed.getRelativePath(), null));
                }
                if (Files.exists(target)) {
                    if (systemPaths.isSystemPath(Paths.get(removed.getRelativePath()))) {
                        LOGGER.info(formatMessage(FORCED, removed.getRelativePath(), HAS_CHANGED_IN_THE_UPDATED_VERSION));
                        Files.createDirectories(installationDir.resolve(removed.getRelativePath()).getParent());
                        IoUtils.copy(target, installationDir.resolve(removed.getRelativePath()));
                    }
                } else {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(formatMessage(REMOVED, removed.getRelativePath(),
                                HAS_BEEN_REMOVED_FROM_THE_UPDATED_VERSION));
                    }
                }
            }
        }
    }

    private void handleAddedFiles(FsDiff fsDiff) throws IOException, ProvisioningException {
        if (fsDiff.hasAddedEntries()) {
            for (FsEntry added : fsDiff.getAddedEntries()) {
                Path p = Paths.get(added.getRelativePath());
                // Ignore .installation owned by prospero
                if (p.getNameCount() > 0) {
                    if (p.getName(0).toString().equals(ProsperoMetadataUtils.METADATA_DIR)) {
                        continue;
                    }
                }
                addFsEntry(updateDir, added, systemPaths);
            }
        }
    }

    private void addFsEntry(Path updateDir, FsEntry added, SystemPaths systemPaths)
            throws ProvisioningException {
        final Path target = updateDir.resolve(added.getRelativePath());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(formatMessage(ADDED, added.getRelativePath(), null));
        }
        if (Files.exists(target)) {
            if (added.isDir()) {
                for (FsEntry child : added.getChildren()) {
                    addFsEntry(updateDir, child, systemPaths);
                }
                return;
            }
            final byte[] targetHash;
            try {
                targetHash = HashUtils.hashPath(target);
            } catch (IOException e) {
                throw new ProvisioningException(Errors.hashCalculation(target), e);
            }

            if (Arrays.equals(added.getHash(), targetHash)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(formatMessage(ADDED, added.getRelativePath(), "Added file matches the update."));
                }
            } else {
                if (systemPaths.isSystemPath(Paths.get(added.getRelativePath()))) {
                    LOGGER.info(formatMessage(FORCED, added.getRelativePath(), CONFLICTS_WITH_THE_UPDATED_VERSION));
                    glold(installationDir.resolve(added.getRelativePath()), target);
                } else {
                    LOGGER.info(formatMessage(CONFLICT, added.getRelativePath(), CONFLICTS_WITH_THE_UPDATED_VERSION));
                    glnew(target, installationDir.resolve(added.getRelativePath()));
                }
            }
        }
    }

    private void handleModifiedFiles(FsDiff fsDiff) throws IOException, ProvisioningException {
        if (fsDiff.hasModifiedEntries()) {
            for (FsEntry[] modified : fsDiff.getModifiedEntries()) {
                FsEntry installation = modified[1];
                FsEntry original = modified[0];
                final Path file = updateDir.resolve(modified[1].getRelativePath());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(formatMessage(MODIFIED, installation.getRelativePath(), null));
                }
                if (Files.exists(file)) {
                    byte[] updateHash;
                    try {
                        updateHash = HashUtils.hashPath(file);
                    } catch (IOException e) {
                        throw new ProvisioningException(Errors.hashCalculation(file), e);
                    }
                    Path installationFile = installationDir.resolve(modified[1].getRelativePath());
                    // Case where the modified file is equal to the hash of the update. Do nothing
                    if (Arrays.equals(installation.getHash(), updateHash)) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug(formatMessage(MODIFIED, installation.getRelativePath(), "Modified file matches the update"));
                        }
                    } else {
                        if (!Arrays.equals(original.getHash(), updateHash)) {
                            if (systemPaths.isSystemPath(Paths.get(installation.getRelativePath()))) {
                                LOGGER.info(formatMessage(FORCED, installation.getRelativePath(), HAS_CHANGED_IN_THE_UPDATED_VERSION));
                                glold(installation.getPath(), file);
                            } else {
                                LOGGER.info(formatMessage(CONFLICT, installation.getRelativePath(), HAS_CHANGED_IN_THE_UPDATED_VERSION));
                                glnew(file, installationFile);
                            }
                        }
                    }
                } else {
                    // The file doesn't exist in the update, we keep the file in the installation
                    LOGGER.info(formatMessage(MODIFIED, installation.getRelativePath(), HAS_BEEN_REMOVED_FROM_THE_UPDATED_VERSION));
                }
            }
        }
    }

    private void doApplyUpdate(FsDiff fsDiff) throws IOException, ProvisioningException {
        // Handles user added/removed/modified files
        handleRemovedFiles(fsDiff);
        handleAddedFiles(fsDiff);
        handleModifiedFiles(fsDiff);

        // Handles files added/removed/modified in the update.
        Path skipUpdateGalleon = PathsUtils.getProvisionedStateDir(updateDir);
        Path skipUpdateInstallation = updateDir.resolve(ProsperoMetadataUtils.METADATA_DIR);
        Path skipInstallationGalleon = PathsUtils.getProvisionedStateDir(installationDir);
        Path skipInstallationInstallation = installationDir.resolve(ProsperoMetadataUtils.METADATA_DIR);

        // Copy the new/modified files that the update brings that are not in the installation and not removed/modified by the user.
        Files.walkFileTree(updateDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Path relative = updateDir.relativize(file);
                Path installationFile = installationDir.resolve(relative);
                // Not a file added or modified by the user
                if (fsDiff.getModifiedEntry(relative.toString()) == null &&
                     fsDiff.getAddedEntry(relative.toString()) == null) {
                    byte[] updateHash = HashUtils.hashPath(file);
                    // The file could be new or updated in the installation
                    if (!Files.exists(installationFile) || !Arrays.equals(updateHash, HashUtils.hashPath(installationFile))) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Copying updated file " + relative + " to the installation");
                        }
                        IoUtils.copy(file, installationFile);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                if (dir.equals(skipUpdateGalleon) || dir.equals(skipUpdateInstallation)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e)
                    throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });

        // Delete the files in the installation that are not present in the update and not added by the user
        // We need to skip .glnew and .glold.
        Files.walkFileTree(installationDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Path relative = installationDir.relativize(file);
                Path updateFile = updateDir.resolve(relative);
                if (fsDiff.getAddedEntry(relative.toString()) == null) {
                    if (!Files.exists(updateFile) &&
                            !updateFile.toString().endsWith(Constants.DOT_GLNEW) &&
                            !updateFile.toString().endsWith(Constants.DOT_GLOLD)) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Deleting the file " + relative + " that doesn't exist in the update");
                        }
                        IoUtils.recursiveDelete(file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                if (dir.equals(skipInstallationGalleon) || dir.equals(skipInstallationInstallation)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!dir.equals(installationDir)) {
                    Path relative = installationDir.relativize(dir);
                    Path target = updateDir.resolve(relative);
                    String pathKey = relative.toString();
                    pathKey = pathKey.endsWith(File.separator) ? pathKey : pathKey + File.separator;
                    if (fsDiff.getAddedEntry(pathKey) == null) {
                        if (!Files.exists(target)) {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("Deleting the directory " + relative + " that doesn't exist in the update");
                            }
                            IoUtils.recursiveDelete(dir);
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    } else {
                         if (!Files.exists(target)) {
                             if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("The directory " + relative + " that doesn't exist in the update is a User changes, skipping it");
                            }
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e)
                    throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void glnew(final Path updateFile, Path installationFile) throws ProvisioningException {
        try {
            IoUtils.copy(updateFile, installationFile.getParent().resolve(installationFile.getFileName() + Constants.DOT_GLNEW));
        } catch (IOException e) {
            throw new ProvisioningException("Failed to persist " + installationFile.getParent().resolve(installationFile.getFileName() + Constants.DOT_GLNEW), e);
        }
    }

    private static void glold(Path installationFile, final Path target) throws ProvisioningException {
        try {
            IoUtils.copy(installationFile, installationFile.getParent().resolve(installationFile.getFileName() + Constants.DOT_GLOLD));
            IoUtils.copy(target, installationFile);
        } catch (IOException e) {
            throw new ProvisioningException("Failed to persist " + target.getParent().resolve(target.getFileName() + Constants.DOT_GLOLD), e);
        }
    }
}
