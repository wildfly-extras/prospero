package org.wildfly.prospero.actions;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.wildfly.prospero.ProsperoLogger;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

/**
 * A temporary record of all files modified, removed or added during applying a candidate server.
 */
class ApplyStageBackup implements AutoCloseable {

    private final Path backupRoot;
    private final Path serverRoot;
    private final Set<Path> addedFiles = new HashSet<>();
    // list of folders where the content is fully backed up
    private final Set<Path> backupRootDirs = new HashSet<>();

    /**
     * create a record for server at {@code serverRoot}. The recorded files will be stored in {@tempRoot}
     *
     * @param serverRoot - root folder of the server that will be updated
     * @param tempRoot - directory to record changed files in
     */
    public ApplyStageBackup(Path serverRoot, Path tempRoot) {
        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
            ProsperoLogger.ROOT_LOGGER.debug("Creating backup record in " + tempRoot);
        }

        this.serverRoot = serverRoot;
        this.backupRoot = tempRoot;
    }

    /**
     * clean up the cache
     */
    @Override
    public void close() {
        addedFiles.clear();
        backupRootDirs.clear();
        FileUtils.deleteQuietly(backupRoot.toFile());
    }

    /**
     * add the {@code file} to the backup cache. If {@code file} is a directory, all of its children (recursive) are
     * added to the backup as well.
     *
     * If the {@code file} does not exist in the {@code serverRoot} at the time this method
     * is called it is assumed that on revert it should be removed.
     *
     * @param file - the file or directory to be backed up
     * @throws IOException - if unable to backup the file
     */
    public void record(Path file) throws IOException {
        if (Files.exists(file)) {
            recordChanged(file);
        } else {
            recordAdded(file);
        }
    }

    /**
     * restore files and directories in {@code targetServer} to the recorded values.
     *
     * @throws IOException - if unable to perform operations of the filesystem
     */
    public void restore() throws IOException {
        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
            ProsperoLogger.ROOT_LOGGER.debug("Restoring server from the backup.");
        }

        // copy backed-up files back into the server
        Files.walkFileTree(backupRoot, restoreModifiedFiles());

        // remove all files added to recorded folders that were not handled by addedFiles
        for (Path root : backupRootDirs) {
            Files.walkFileTree(root, deleteNewFiles());
        }

        // remove explicitly added files
        removeRemainingAddedFiles();
    }

    private void recordChanged(Path file) throws IOException {
        if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
            ProsperoLogger.ROOT_LOGGER.trace("Recording existing file " + file + " for backup.");
        }

        final Path relativePath = serverRoot.relativize(file);

        final Path parentDir = relativePath.getParent();
        if (parentDir != null && !Files.exists(backupRoot.resolve(parentDir))) {
            Files.createDirectories(backupRoot.resolve(parentDir));
        }

        if (Files.isDirectory(file)) {
            if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
                ProsperoLogger.ROOT_LOGGER.trace("Setting folder " + file + " as backup root.");
            }

            backupRootDirs.add(file);
            FileUtils.copyDirectory(file.toFile(), backupRoot.resolve(relativePath).toFile());
        } else {
            Files.copy(file, backupRoot.resolve(relativePath), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void recordAdded(Path addedFile) {
        if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
            ProsperoLogger.ROOT_LOGGER.trace("Recording new file " + addedFile + " for backup.");
        }

        if (addedFile.equals(serverRoot)) {
            // we're done don't recurse anymore
            return;
        }

        if (Files.exists(addedFile.getParent())) {
            addedFiles.add(addedFile);
        } else {
            // we need to add parent folders to make sure that folders that were created are removed
            recordAdded(addedFile.getParent());
            addedFiles.add(addedFile);
        }
    }

    private SimpleFileVisitor<Path> deleteNewFiles() {
        return new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                final Path relativePath = serverRoot.relativize(file);
                if (!Files.exists(backupRoot.resolve(relativePath))) {
                    if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
                        ProsperoLogger.ROOT_LOGGER.trace("Removing added file " + relativePath);
                    }

                    Files.delete(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                final Path relativePath = serverRoot.relativize(dir);
                if (!Files.exists(backupRoot.resolve(relativePath))) {
                    if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
                        ProsperoLogger.ROOT_LOGGER.trace("Removing added directory " + relativePath);
                    }

                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        };
    }

    private void removeRemainingAddedFiles() throws IOException {
        for (Path addedFile : addedFiles) {
            if (Files.isDirectory(addedFile)) {
                if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
                    ProsperoLogger.ROOT_LOGGER.trace("Removing added directory " + addedFile);
                }

                FileUtils.deleteDirectory(addedFile.toFile());
            } else {
                if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
                    ProsperoLogger.ROOT_LOGGER.trace("Removing added file " + addedFile);
                }

                Files.deleteIfExists(addedFile);
            }
        }
    }

    private SimpleFileVisitor<Path> restoreModifiedFiles() {
        return new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                final Path relativePath = backupRoot.relativize(file);

                final Path parentDir = relativePath.getParent();
                if (parentDir != null && !Files.exists(serverRoot.resolve(parentDir))) {
                    if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
                        ProsperoLogger.ROOT_LOGGER.trace("Recreating removed directory " + parentDir);
                    }

                    Files.createDirectories(serverRoot.resolve(parentDir));
                }

                final Path targetFile = serverRoot.resolve(relativePath);
                if (fileChanged(file, targetFile)) {
                    if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
                        ProsperoLogger.ROOT_LOGGER.trace("Restoring changed file " + relativePath);
                    }

                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        };
    }

    private static boolean fileChanged(Path file, Path targetFile) throws IOException {
        if (!Files.exists(targetFile)) {
            return true;
        }

        try (FileInputStream fis1 = new FileInputStream(targetFile.toFile());
             FileInputStream fis2 = new FileInputStream(file.toFile())){
            return !IOUtils.contentEquals(fis1, fis2);
        }
    }
}
