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
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.aether.artifact.Artifact;
import org.jboss.galleon.Constants;
import org.jboss.galleon.Errors;

import org.jboss.galleon.ProvisioningManager;
import org.wildfly.prospero.ProsperoLogger;
import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.FileConflict;
import org.wildfly.prospero.api.InstallationMetadata;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.api.SavedState;
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
import static org.wildfly.prospero.metadata.ProsperoMetadataUtils.CURRENT_VERSION_FILE;
import static org.wildfly.prospero.metadata.ProsperoMetadataUtils.METADATA_DIR;

import org.jboss.galleon.diff.FsEntry;
import org.jboss.galleon.layout.SystemPaths;
import org.jboss.galleon.util.HashUtils;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.PathsUtils;
import org.wildfly.prospero.galleon.ArtifactCache;
import org.wildfly.prospero.galleon.GalleonEnvironment;
import org.wildfly.prospero.installation.git.GitStorage;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;
import org.wildfly.prospero.updates.MarkerFile;
import org.wildfly.prospero.updates.UpdateSet;
import org.wildfly.prospero.wfchannel.MavenSessionManager;

/**
 * Merges a "candidate" server into base server. The "candidate" can be an update or revert.
 */
public class ApplyCandidateAction {
    public static final Path STANDALONE_STARTUP_MARKER = Path.of("standalone", "tmp", "startup-marker");
    public static final Path DOMAIN_STARTUP_MARKER = Path.of("domain", "tmp", "startup-marker");
    private final Path updateDir;
    private final Path installationDir;
    private final SystemPaths systemPaths;

    public enum Type {
        UPDATE("UPDATE"), REVERT("REVERT");

        private final String text;

        Type(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public static Type from (final String text) {
            switch (text) {
                case "UPDATE":
                    return ApplyCandidateAction.Type.UPDATE;
                case "REVERT":
                    return ApplyCandidateAction.Type.REVERT;
                default:
                    throw ProsperoLogger.ROOT_LOGGER.invalidMarkerFileOperation(text);
            }
        }
    }

    public ApplyCandidateAction(Path installationDir, Path updateDir)
            throws ProvisioningException, OperationException {
        this.updateDir = updateDir;
        this.installationDir = installationDir;

        try {
            this.systemPaths = SystemPaths.load(updateDir);
        } catch (IOException ex) {
            throw new ProvisioningException(ex);
        }
        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
            ProsperoLogger.ROOT_LOGGER.debug("System paths " + this.systemPaths.getPaths());
        }
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
    public List<FileConflict> applyUpdate(Type operation) throws ProvisioningException, OperationException {
        if (ValidationResult.OK != verifyCandidate(operation)) {
            final InvalidUpdateCandidateException ex = ProsperoLogger.ROOT_LOGGER.invalidUpdateCandidate(updateDir, installationDir);
            ProsperoLogger.ROOT_LOGGER.warn("", ex);
            throw ex;
        }

        if (targetServerIsRunning()) {
            final ProvisioningException ex = ProsperoLogger.ROOT_LOGGER.serverRunningError();
            ProsperoLogger.ROOT_LOGGER.warn("", ex);
            throw ex;
        }

        FsDiff diffs = findChanges();
        try {
            ProsperoLogger.ROOT_LOGGER.applyingCandidate(operation.text.toLowerCase(Locale.ROOT), updateDir);
            ProsperoLogger.ROOT_LOGGER.candidateChanges(
                    findUpdates().getArtifactUpdates().stream().map(ArtifactChange::prettyPrint).collect(Collectors.joining("; "))
                    );

            final List<FileConflict> conflicts = doApplyUpdate(diffs);

            if (conflicts.isEmpty()) {
                ProsperoLogger.ROOT_LOGGER.noCandidateConflicts();
            } else {
                ProsperoLogger.ROOT_LOGGER.candidateConflicts(
                        conflicts.stream().map(FileConflict::prettyPrint).collect(Collectors.joining("; "))
                        );
                for (FileConflict conflict : conflicts) {
                    ProsperoLogger.ROOT_LOGGER.info(conflict.prettyPrint());
                }
            }

            updateMetadata(operation);
            ProsperoLogger.ROOT_LOGGER.candidateApplied(operation.text, installationDir);
            return conflicts;
        } catch (IOException ex) {
            throw new ProvisioningException(ex);
        }
    }

    public enum ValidationResult {
        OK, NOT_CANDIDATE, STALE, WRONG_TYPE;
    }

    /**
     * checks that the candidate is an update of a current state of installation
     *
     * @return true if the candidate can be applied to installation
     * @throws InvalidUpdateCandidateException - if the candidate has no marker file
     * @throws MetadataException - if the metadata of candidate or installation cannot be read
     */
    public ValidationResult verifyCandidate(Type operation) throws InvalidUpdateCandidateException, MetadataException {
        final Path updateMarkerPath = updateDir.resolve(MarkerFile.UPDATE_MARKER_FILE);
        if (!Files.exists(updateMarkerPath)) {
            if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                ProsperoLogger.ROOT_LOGGER.debugf("The candidate [%s] doesn't have a marker file", updateDir);
            }
            return ValidationResult.NOT_CANDIDATE;
        }
        try {
            final MarkerFile marker = MarkerFile.read(updateDir);

            final String hash = marker.getState();
            if (!InstallationMetadata.loadInstallation(installationDir).getRevisions().get(0).getName().equals(hash)) {
                if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                    ProsperoLogger.ROOT_LOGGER.debugf("The installation state has changed from the candidate [%s].", updateDir);
                }
                return ValidationResult.STALE;
            }

            if (marker.getOperation() != operation) {
                if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                    ProsperoLogger.ROOT_LOGGER.debugf("The candidate server has been prepared for different operation [%s].", marker.getOperation().getText());
                }
                return ValidationResult.WRONG_TYPE;
            }
        } catch (IOException e) {
            if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                ProsperoLogger.ROOT_LOGGER.debugf("Unable to read marker file [%s].", updateDir);
            }
            throw ProsperoLogger.ROOT_LOGGER.unableToReadFile(updateMarkerPath, e);
        }
        return ValidationResult.OK;
    }

    /**
     * list conflicts between the candidate ({@code installationDir} and target server {@code updateDir}.
     *
     *
     * @return list of {@code FileConflict} or empty list if no conflicts found.
     * @throws ProvisioningException
     * @throws OperationException
     */
    public List<FileConflict> getConflicts() throws ProvisioningException, OperationException {
        try {
            return compareServers(findChanges());
        } catch (IOException ex) {
            throw new ProvisioningException(ex);
        }
    }

    /**
     * list artifacts changed between base and candidate servers.
     *
     * @return list of changes
     * @throws OperationException
     */
    public UpdateSet findUpdates() throws OperationException {
        final Map<String, Artifact> baseMap = new HashMap<>();
        final Map<String, Artifact> candidateMap = new HashMap<>();
        final List<Artifact> base;
        final List<Artifact> candidate;

        try (final InstallationMetadata metadata = InstallationMetadata.loadInstallation(installationDir)) {
            base = metadata.getArtifacts();
        }
        try (final InstallationMetadata metadata = InstallationMetadata.loadInstallation(updateDir)) {
            candidate = metadata.getArtifacts();
        }

        for (Artifact artifact : base) {
            baseMap.put(artifact.getGroupId() + ":" + artifact.getArtifactId(), artifact);
        }
        for (Artifact artifact : candidate) {
            candidateMap.put(artifact.getGroupId() + ":" + artifact.getArtifactId(), artifact);
        }
        List<ArtifactChange> changes = new ArrayList<>();
        for (String key : baseMap.keySet()) {
            if (candidateMap.containsKey(key)) {
                if (!baseMap.get(key).getVersion().equals(candidateMap.get(key).getVersion())) {
                    changes.add(ArtifactChange.updated(baseMap.get(key), candidateMap.get(key)));
                }
            } else {
                changes.add(ArtifactChange.removed(baseMap.get(key)));
            }
        }

        for (String key : candidateMap.keySet()) {
            if (!baseMap.containsKey(key)) {
                changes.add(ArtifactChange.added(baseMap.get(key)));
            }
        }

        return new UpdateSet(changes);
    }

    /**
     * returns the revision of the candidate server
     * @return {@code SavedState}
     * @throws MetadataException - if unable to read the candidate server metadata
     */
    public SavedState getCandidateRevision() throws MetadataException {
        try (final InstallationMetadata metadata = InstallationMetadata.loadInstallation(updateDir)) {
            return metadata.getRevisions().get(0);
        }
    }

    private boolean targetServerIsRunning() {
        return Files.exists(installationDir.resolve(STANDALONE_STARTUP_MARKER)) || Files.exists(installationDir.resolve(DOMAIN_STARTUP_MARKER));
    }

    private FsDiff findChanges() throws ProvisioningException, OperationException {
        // offline is enough - we just need to read the configuration
        final MavenOptions mavenOptions = MavenOptions.builder()
                .setOffline(true)
                .setNoLocalCache(true)
                .build();
        try (final GalleonEnvironment galleonEnv = GalleonEnvironment.builder(installationDir, Collections.emptyList(),
                        new MavenSessionManager(mavenOptions))
                .build()) {
            ProvisioningManager provisioningManager = galleonEnv.getProvisioningManager();
            return provisioningManager.getFsDiff();
        }

    }

    private void updateMetadata(Type operation) throws ProvisioningException, MetadataException {
        try {
            copyCurrentVersions();
            writeProsperoMetadata(operation);
            updateInstallationCache();
            Path installationGalleonPath = PathsUtils.getProvisionedStateDir(installationDir);
            Path updateGalleonPath = PathsUtils.getProvisionedStateDir(updateDir);
            IoUtils.recursiveDelete(installationGalleonPath);
            IoUtils.copy(updateGalleonPath, installationGalleonPath, true);
        } catch (IOException ex) {
            throw new ProvisioningException(ex);
        }
    }

    private void copyCurrentVersions() throws IOException {
        Path sourceVersions = updateDir.resolve(METADATA_DIR).resolve(CURRENT_VERSION_FILE);
        if (Files.exists(sourceVersions)) {
            Files.copy(sourceVersions, installationDir.resolve(METADATA_DIR).resolve(CURRENT_VERSION_FILE), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeProsperoMetadata(Type operation) throws MetadataException, IOException {
        Path updateMetadataDir = updateDir.resolve(ProsperoMetadataUtils.METADATA_DIR);
        Path updateManifest = updateMetadataDir.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME);

        Path installationMetadataDir = installationDir.resolve(ProsperoMetadataUtils.METADATA_DIR);
        Path installationManifest = installationMetadataDir.resolve(ProsperoMetadataUtils.MANIFEST_FILE_NAME);
        IoUtils.copy(updateManifest, installationManifest);

        try (GitStorage git = new GitStorage(installationDir)) {
            git.recordChange(operation==Type.UPDATE? SavedState.Type.UPDATE:SavedState.Type.ROLLBACK);
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
                if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                    ProsperoLogger.ROOT_LOGGER.debug(formatMessage(REMOVED, removed.getRelativePath(), null));
                }
                if (Files.exists(target)) {
                    if (systemPaths.isSystemPath(Paths.get(removed.getRelativePath()))) {
                        conflictList.add(FileConflict.userRemoved(removed.getRelativePath()).updateModified().overwritten());
                        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                            ProsperoLogger.ROOT_LOGGER.debug(formatMessage(FORCED, removed.getRelativePath(), HAS_CHANGED_IN_THE_UPDATED_VERSION));
                        }
                        Files.createDirectories(installationDir.resolve(removed.getRelativePath()).getParent());
                        IoUtils.copy(target, installationDir.resolve(removed.getRelativePath()));
                    }
                } else {
                    if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                        ProsperoLogger.ROOT_LOGGER.debug(formatMessage(REMOVED, removed.getRelativePath(),
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
        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
            ProsperoLogger.ROOT_LOGGER.debug(formatMessage(ADDED, added.getRelativePath(), null));
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
                if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                    ProsperoLogger.ROOT_LOGGER.debug(formatMessage(ADDED, added.getRelativePath(), "Added file matches the update."));
                }
            } else {
                if (systemPaths.isSystemPath(Paths.get(added.getRelativePath()))) {
                    if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                        ProsperoLogger.ROOT_LOGGER.debug(formatMessage(FORCED, added.getRelativePath(), CONFLICTS_WITH_THE_UPDATED_VERSION));
                    }
                    conflictList.add(FileConflict.userAdded(added.getRelativePath()).updateAdded().overwritten());
                    glold(installationDir.resolve(added.getRelativePath()), target);
                } else {
                    if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                        ProsperoLogger.ROOT_LOGGER.debug(formatMessage(CONFLICT, added.getRelativePath(), CONFLICTS_WITH_THE_UPDATED_VERSION));
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
                if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                    ProsperoLogger.ROOT_LOGGER.debug(formatMessage(MODIFIED, installation.getRelativePath(), null));
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
                        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                            ProsperoLogger.ROOT_LOGGER.debug(formatMessage(MODIFIED, installation.getRelativePath(), "Modified file matches the update"));
                        }
                    } else {
                        if (!Arrays.equals(original.getHash(), updateHash)) {
                            if (systemPaths.isSystemPath(Paths.get(installation.getRelativePath()))) {
                                if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                                    ProsperoLogger.ROOT_LOGGER.debug(formatMessage(FORCED, installation.getRelativePath(), HAS_CHANGED_IN_THE_UPDATED_VERSION));
                                }
                                conflictList.add(FileConflict.userModified(installation.getRelativePath()).updateModified().overwritten());
                                glold(installation.getPath(), file);
                            } else {
                                if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                                    ProsperoLogger.ROOT_LOGGER.debug(formatMessage(CONFLICT, installation.getRelativePath(), HAS_CHANGED_IN_THE_UPDATED_VERSION));
                                }
                                conflictList.add(FileConflict.userModified(installation.getRelativePath()).updateModified().userPreserved());
                                glnew(file, installationFile);
                            }
                        }
                    }
                } else {
                    // The file doesn't exist in the update, we keep the file in the installation
                    if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                        ProsperoLogger.ROOT_LOGGER.debug(formatMessage(MODIFIED, installation.getRelativePath(), HAS_BEEN_REMOVED_FROM_THE_UPDATED_VERSION));
                    }
                    conflictList.add(FileConflict.userModified(installation.getRelativePath()).updateRemoved().userPreserved());
                }
            }
        }
        return conflictList;
    }

    private List<FileConflict> compareServers(FsDiff fsDiff) throws IOException, ProvisioningException {
        List<FileConflict> conflicts = new ArrayList<>();
        // Handles user added/removed/modified files
        conflicts.addAll(handleRemovedFiles(fsDiff));
        conflicts.addAll(handleAddedFiles(fsDiff));
        conflicts.addAll(handleModifiedFiles(fsDiff));
        return Collections.unmodifiableList(conflicts);
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
                        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                            ProsperoLogger.ROOT_LOGGER.debug("Copying updated file " + relative + " to the installation");
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
                        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                            ProsperoLogger.ROOT_LOGGER.debug("Deleting the file " + relative + " that doesn't exist in the update");
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
                        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                            ProsperoLogger.ROOT_LOGGER.debug("The directory " + relative + " that doesn't exist in the update is a User changes, skipping it");
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
                            if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
                                ProsperoLogger.ROOT_LOGGER.debug("Deleting the directory " + relative + " that doesn't exist in the update");
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
