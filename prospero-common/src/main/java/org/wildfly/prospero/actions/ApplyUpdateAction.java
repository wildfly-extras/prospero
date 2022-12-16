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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jboss.galleon.Constants;
import org.jboss.galleon.Errors;

import org.jboss.galleon.ProvisioningManager;
import org.wildfly.prospero.api.FileConflict;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.exceptions.InvalidUpdateCandidateException;
import org.wildfly.prospero.api.exceptions.MetadataException;
import org.wildfly.prospero.api.exceptions.OperationException;
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
import org.wildfly.prospero.galleon.ArtifactCache;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.installation.git.GitStorage;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

public class ApplyUpdateAction {
    public static final Path UPDATE_MARKER_FILE = Path.of(InstallationMetadata.METADATA_DIR, ".update.txt");
    private static final Logger LOGGER = Logger.getLogger(ApplyUpdateAction.class);
    public static final Path STANDALONE_STARTUP_MARKER = Path.of("standalone", "tmp", "startup-marker");
    public static final Path DOMAIN_STARTUP_MARKER = Path.of("domain", "tmp", "startup-marker");
    private final Path updateDir;
    private final Path installationDir;
    private final SystemPaths systemPaths;
    private final ProvisioningManager provisioningManager;

    public ApplyUpdateAction(Path installationDir, Path updateDir)
            throws ProvisioningException, OperationException {
        this.updateDir = updateDir;
        this.installationDir = installationDir;

        try {
            this.systemPaths = SystemPaths.load(updateDir);
        } catch (IOException ex) {
            throw new ProvisioningException(ex);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("System paths " + this.systemPaths.getPaths());
        }
        // offline is enough - we just need to read the configuration
        provisioningManager = GalleonEnvironment.builder(installationDir, Collections.emptyList(),
                new MavenSessionManager(Optional.empty(), true))
                .build().getProvisioningManager();
    }

    /**
     * Applies changes from prepare update at {@code updateDir} to {@code installationDir}. The update candidate has to
     * contain a marker file {@code .installation/.update.txt} with revision matching current installation's revision.
     * Any update files from {@code updateDir} are copied to {@code installationDir}. If any of the updates
     * (apart from {@code system-paths}) conflict with user changes, the user changes are preserved and the updated file
     * is added with {@code'.glnew'} suffix.
     *
     *
     * @return list of solved {@code FileConflict}s
     * @throws ProvisioningException - if unable to apply the changes from {@code updateDir} to {@code installationDir}
     * @throws InvalidUpdateCandidateException - if the folder at {@code updateDir} is not a valid update
     * @throws MetadataException - if unable to read or write the installation of update metadata
     */
    public List<FileConflict> applyUpdate() throws ProvisioningException, InvalidUpdateCandidateException, MetadataException {
        if (!verifyUpdateCandidate()) {
            throw Messages.MESSAGES.invalidUpdateCandidate(updateDir, installationDir);
        }

        if (targetServerIsRunning()) {
            throw new ProvisioningException("The server appears to be running.");
        }

        FsDiff diffs = findChanges();
        try {
            final List<FileConflict> conflicts = doApplyUpdate(diffs);
            updateMetadata();
            return conflicts;
        } catch (IOException ex) {
            throw new ProvisioningException(ex);
        }
    }

    /**
     * checks that the candidate is an update of a current state of installation
     *
     * @return true if the candidate can be applied to installation
     * @throws InvalidUpdateCandidateException - if the candidate has no marker file
     * @throws MetadataException - if the metadata of candidate or installation cannot be read
     */
    public boolean verifyUpdateCandidate() throws InvalidUpdateCandidateException, MetadataException {
        final Path updateMarkerPath = updateDir.resolve(UPDATE_MARKER_FILE);
        if (!Files.exists(updateMarkerPath)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("The update candidate [%s] doesn't have a marker file", updateDir);
            }
            throw Messages.MESSAGES.invalidUpdateCandidate(updateDir, installationDir);
        }
        try {
            final String hash = Files.readString(updateMarkerPath);
            if (!new InstallationMetadata(installationDir).getRevisions().get(0).getName().equals(hash)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debugf("The installation state has changed from the update candidate [%s].", updateDir);
                }
                return false;
            }
        } catch (IOException e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("Unable to read marker file [%s].", updateDir);
            }
            throw Messages.MESSAGES.unableToReadFile(updateMarkerPath, e);
        }
        return true;
    }

    private boolean targetServerIsRunning() {
        return Files.exists(installationDir.resolve(STANDALONE_STARTUP_MARKER)) || Files.exists(installationDir.resolve(DOMAIN_STARTUP_MARKER));
    }

    private FsDiff findChanges() throws ProvisioningException {
        return provisioningManager.getFsDiff();
    }

    private void updateMetadata() throws ProvisioningException, MetadataException {
        try {
            writeProsperoMetadata();
            updateInstallationCache();
            Path installationGalleonPath = PathsUtils.getProvisionedStateDir(installationDir);
            Path updateGalleonPath = PathsUtils.getProvisionedStateDir(updateDir);
            IoUtils.recursiveDelete(installationGalleonPath);
            IoUtils.copy(updateGalleonPath, installationGalleonPath, true);
        } catch (IOException ex) {
            throw new ProvisioningException(ex);
        }
    }

    private void writeProsperoMetadata() throws MetadataException, IOException {
        Path updateMetadataDir = updateDir.resolve(ProsperoMetadataUtils.METADATA_DIR);
        Path updateManifest = updateMetadataDir.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME);

        Path installationMetadataDir = installationDir.resolve(ProsperoMetadataUtils.METADATA_DIR);
        Path installationManifest = installationMetadataDir.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME);
        IoUtils.copy(updateManifest, installationManifest);

        GitStorage git = new GitStorage(installationDir);
        try {
            git.record();
        } finally {
            try {
                git.close();
            } catch (Exception e) {
                // log and ignore
                Messages.MESSAGES.unableToCloseStore(e);
            }
        }
    }

    private void updateInstallationCache() throws IOException {
        Path updateCacheDir = updateDir.resolve(ArtifactCache.CACHE_FOLDER);


        Path installationCacheDir = installationDir.resolve(ArtifactCache.CACHE_FOLDER);
        if (Files.exists(installationCacheDir)) {
            IoUtils.recursiveDelete(installationCacheDir);
        }
        if (Files.exists(updateCacheDir)) {
            IoUtils.copy(updateCacheDir, installationCacheDir);
        }
    }

    private List<FileConflict> handleRemovedFiles(FsDiff fsDiff) throws IOException {
        final List<FileConflict> conflictList = new ArrayList<>();
        if (fsDiff.hasRemovedEntries()) {
            for (FsEntry removed : fsDiff.getRemovedEntries()) {
                final Path target = updateDir.resolve(removed.getRelativePath());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(formatMessage(REMOVED, removed.getRelativePath(), null));
                }
                if (Files.exists(target)) {
                    if (systemPaths.isSystemPath(Paths.get(removed.getRelativePath()))) {
                        conflictList.add(FileConflict.userRemoved(removed.getRelativePath()).updateModified().overwritten());
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug(formatMessage(FORCED, removed.getRelativePath(), HAS_CHANGED_IN_THE_UPDATED_VERSION));
                        }
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
        return conflictList;
    }

    private List<FileConflict> handleAddedFiles(FsDiff fsDiff) throws IOException, ProvisioningException {
        final List<FileConflict> conflictList = new ArrayList<>();
        if (fsDiff.hasAddedEntries()) {
            for (FsEntry added : fsDiff.getAddedEntries()) {
                Path p = Paths.get(added.getRelativePath());
                // Ignore .installation owned by prospero
                if (p.getNameCount() > 0) {
                    if (p.getName(0).toString().equals(ProsperoMetadataUtils.METADATA_DIR)) {
                        continue;
                    }
                }
                addFsEntry(updateDir, added, systemPaths, conflictList);
            }
        }
        return conflictList;
    }

    private void addFsEntry(Path updateDir, FsEntry added, SystemPaths systemPaths, List<FileConflict> conflictList)
            throws ProvisioningException {
        final Path target = updateDir.resolve(added.getRelativePath());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(formatMessage(ADDED, added.getRelativePath(), null));
        }
        if (Files.exists(target)) {
            if (added.isDir()) {
                for (FsEntry child : added.getChildren()) {
                    addFsEntry(updateDir, child, systemPaths, conflictList);
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
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(formatMessage(FORCED, added.getRelativePath(), CONFLICTS_WITH_THE_UPDATED_VERSION));
                    }
                    conflictList.add(FileConflict.userAdded(added.getRelativePath()).updateAdded().overwritten());
                    glold(installationDir.resolve(added.getRelativePath()), target);
                } else {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(formatMessage(CONFLICT, added.getRelativePath(), CONFLICTS_WITH_THE_UPDATED_VERSION));
                    }
                    conflictList.add(FileConflict.userAdded(added.getRelativePath()).updateAdded().userPreserved());
                    glnew(target, installationDir.resolve(added.getRelativePath()));
                }
            }
        }
    }

    private List<FileConflict> handleModifiedFiles(FsDiff fsDiff) throws IOException, ProvisioningException {
        final List<FileConflict> conflictList = new ArrayList<>();
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
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug(formatMessage(FORCED, installation.getRelativePath(), HAS_CHANGED_IN_THE_UPDATED_VERSION));
                                }
                                conflictList.add(FileConflict.userModified(installation.getRelativePath()).updateModified().overwritten());
                                glold(installation.getPath(), file);
                            } else {
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug(formatMessage(CONFLICT, installation.getRelativePath(), HAS_CHANGED_IN_THE_UPDATED_VERSION));
                                }
                                conflictList.add(FileConflict.userModified(installation.getRelativePath()).updateModified().userPreserved());
                                glnew(file, installationFile);
                            }
                        }
                    }
                } else {
                    // The file doesn't exist in the update, we keep the file in the installation
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug(formatMessage(MODIFIED, installation.getRelativePath(), HAS_BEEN_REMOVED_FROM_THE_UPDATED_VERSION));
                    }
                    conflictList.add(FileConflict.userModified(installation.getRelativePath()).updateRemoved().userPreserved());
                }
            }
        }
        return conflictList;
    }

    private List<FileConflict> doApplyUpdate(FsDiff fsDiff) throws IOException, ProvisioningException {
        List<FileConflict> conflicts = new ArrayList<>();
        // Handles user added/removed/modified files
        conflicts.addAll(handleRemovedFiles(fsDiff));
        conflicts.addAll(handleAddedFiles(fsDiff));
        conflicts.addAll(handleModifiedFiles(fsDiff));

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
                final String pathKey = getFsDiffKey(relative, false);
                if (fsDiff.getModifiedEntry(pathKey) == null &&
                        (fsDiff.getAddedEntry(pathKey) == null && !isParentAdded(fsDiff, relative))) {
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

            private boolean isParentAdded(FsDiff fsDiff, Path relative) {
                Path parent = relative.getParent();
                while (parent != null) {
                    // FsDiff always uses UNIX separators
                    if (fsDiff.getAddedEntry(parent + "/") != null) {
                        return true;
                    }
                    parent = parent.getParent();
                }
                return false;
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
                final String fsDiffKey = getFsDiffKey(relative, false);
                if (fsDiff.getAddedEntry(fsDiffKey) == null && fsDiff.getModifiedEntry(fsDiffKey) == null) {
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
                    String pathKey = getFsDiffKey(relative, true);
                    if (fsDiff.getAddedEntry(pathKey) != null && !Files.exists(target)) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("The directory " + relative + " that doesn't exist in the update is a User changes, skipping it");
                        }
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e)
                    throws IOException {
                if (!dir.equals(installationDir)) {
                    Path relative = installationDir.relativize(dir);
                    Path target = updateDir.resolve(relative);
                    String pathKey = getFsDiffKey(relative, true);
                    if (fsDiff.getAddedEntry(pathKey) == null) {
                        if (!Files.exists(target) && dir.toFile().list().length == 0) {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("Deleting the directory " + relative + " that doesn't exist in the update");
                            }
                            IoUtils.recursiveDelete(dir);
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return Collections.unmodifiableList(conflicts);
    }

    private String getFsDiffKey(Path relative, boolean appendSeparator) {
        String pathKey = relative.toString().replace(File.separator, "/");
        if (appendSeparator) {
            // FsDiff always uses UNIX separators
            pathKey = pathKey.endsWith("/") ? pathKey : pathKey + "/";
        }
        return pathKey;
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
