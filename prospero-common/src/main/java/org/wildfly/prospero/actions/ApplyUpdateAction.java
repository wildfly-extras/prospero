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
import org.jboss.galleon.diff.FsEntry;
import org.jboss.galleon.layout.SystemPaths;
import org.jboss.galleon.util.HashUtils;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.prospero.galleon.ChannelMavenArtifactRepositoryManager;

public class ApplyUpdateAction implements AutoCloseable {

    private final InstallationMetadata installationMetadata;
    private final MavenSessionManager mavenSessionManager;
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
        System.out.println("SYSTEM PATHS " + this.systemPaths.getPaths());
        galleonEnv = GalleonEnvironment
                .builder(installationDir, prosperoConfig, mavenSessionManager)
                .setConsole(console)
                .build();
        this.mavenSessionManager = mavenSessionManager;
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
            writeProsperoMetadata(installationDir, galleonEnv.getRepositoryManager(), installationMetadata.getProsperoConfig().getChannels());
            IoUtils.recursiveDelete(installationDir.resolve(".galleon"));
            IoUtils.copy(updateDir.resolve(".galleon"), installationDir.resolve(".galleon"), true);
        } catch (IOException | MetadataException ex) {
            throw new ProvisioningException(ex);
        }
    }

    private void writeProsperoMetadata(Path home, ChannelMavenArtifactRepositoryManager maven, List<Channel> channels) throws MetadataException {
        final ChannelManifest manifest = maven.resolvedChannel();

        try (final InstallationMetadata installationMetadata = new InstallationMetadata(home, manifest, channels)) {
            installationMetadata.recordProvision(true, true);
        }
    }

    private void doApplyUpdate(FsDiff fsDiff) throws IOException, ProvisioningException {
        if (fsDiff.hasRemovedEntries()) {
            for (FsEntry removed : fsDiff.getRemovedEntries()) {
                final Path target = updateDir.resolve(removed.getRelativePath());
                System.out.println("REMOVED FILE " + target);
                if (Files.exists(target)) {
                    if (systemPaths.isSystemPath(Paths.get(removed.getRelativePath()))) {
                        System.out.println("Forcing a copy of a deleted file");
                        Files.createDirectories(installationDir.resolve(removed.getRelativePath()).getParent());
                        IoUtils.copy(target, installationDir.resolve(removed.getRelativePath()));
                    } else {
                        System.out.println(removed.getRelativePath() + " Has already been removed in installation");
                    }
                } else {
                    System.out.println(removed.getRelativePath() + " is also removed in update");
                }
            }
        }
        if (fsDiff.hasAddedEntries()) {
            for (FsEntry added : fsDiff.getAddedEntries()) {
                Path p = Paths.get(added.getRelativePath());
                if (p.getName(0).toString().equals(".installation")) {
                    continue;
                }
                addFsEntry(updateDir, added, systemPaths);
            }
        }
        if (fsDiff.hasModifiedEntries()) {
            for (FsEntry[] modified : fsDiff.getModifiedEntries()) {
                FsEntry installation = modified[1];
                FsEntry original = modified[0];
                final Path file = updateDir.resolve(modified[1].getRelativePath());
                byte[] updateHash = HashUtils.hashPath(file);
                Path installationFile = installationDir.resolve(modified[1].getRelativePath());
                System.out.println("MODIFIED " + installation.getRelativePath());
                // Case where the modified file is equal to the hash of the update. Do nothing
                if (Arrays.equals(installation.getHash(), updateHash)) {
                    System.out.println("Installation changes match the update");
                } else {
                    if (!Arrays.equals(original.getHash(), updateHash)) {
                        if (systemPaths.isSystemPath(Paths.get(installation.getRelativePath()))) {
                            System.out.println("SYSTEM PATH");
                            glold(installation.getPath(), file);
                        } else {
                            System.out.println("CONFLICT, create glnew " + installationFile + ".glnew");
                            glnew(file, installationFile);
                        }
                    } else {
                        System.out.println("Installation and update are identical, do not copy the file");
                    }
                }
            }
        }
        // We have handled user added/removed/modified files
        // Need to handle files added/removed/modified in the update.

        Path skipUpdateGalleon = updateDir.resolve(".galleon");
        Path skipUpdateInstallation = updateDir.resolve(".installation");
        Path skipInstallationGalleon = installationDir.resolve(".galleon");
        Path skipInstallationInstallation = installationDir.resolve(".installation");
        Files.walkFileTree(updateDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                //               try {
                Path relative = updateDir.relativize(file);
                Path installationFile = installationDir.resolve(relative);
                // Not a file modified by the user
                if (fsDiff.getModifiedEntry(relative.toString()) == null
                        && fsDiff.getAddedEntry(relative.toString()) == null
                        && fsDiff.getRemovedEntry(relative.toString()) == null) {
                    byte[] updateHash = HashUtils.hashPath(file);
                    // The file could be new or updated in the installation
                    if (!Files.exists(installationFile) || !Arrays.equals(updateHash, HashUtils.hashPath(installationFile))) {
                        System.out.println("Copying file to the installation " + file);
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

        Files.walkFileTree(installationDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                //               try {
                Path relative = installationDir.relativize(file);
                Path updateFile = updateDir.resolve(relative);
                // Not a file in the update
                if (fsDiff.getModifiedEntry(relative.toString()) == null
                        && fsDiff.getAddedEntry(relative.toString()) == null
                        && fsDiff.getRemovedEntry(relative.toString()) == null) {
                    if (!Files.exists(updateFile) && !updateFile.toString().endsWith(".glnew") && !updateFile.toString().endsWith(".glold")) {
                         System.out.println("Deleting the file " + file + " that doesn't exist in the update");
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
                    Path target = updateDir.resolve(dir);
                    // Not a file in the update
                    if (fsDiff.getModifiedEntry(relative.toString()) == null
                            && fsDiff.getAddedEntry(relative.toString()) == null
                            && fsDiff.getRemovedEntry(relative.toString()) == null) {
                        if (!Files.exists(target)) {
                            System.out.println("Deleting the directory " + dir + " that doesn't exist in the update");
                            IoUtils.recursiveDelete(dir);
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

    private void addFsEntry(Path updateDir, FsEntry added, SystemPaths systemPaths)
            throws ProvisioningException {
        final Path target = updateDir.resolve(added.getRelativePath());
        System.out.println("ADDED FILE " + target);
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
                System.out.println("Added match the update, nothing to do for " + added.getRelativePath());
            } else {
                if (systemPaths.isSystemPath(Paths.get(added.getRelativePath()))) {
                    System.out.println("The added file in the update must override the file in the installation");
                    glold(installationDir.resolve(added.getRelativePath()), target);
                } else {
                    glnew(target, installationDir.resolve(added.getRelativePath()));
                }
            }
        } else {
            System.out.println("This file was added by the user, do nothing for " + target);
        }
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
