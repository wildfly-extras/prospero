package org.wildfly.prospero.actions;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.wildfly.prospero.ProsperoLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * A temporary record of all files modified, removed or added during applying a candidate server.
 */
class ApplyStageBackup implements AutoCloseable {

    protected static final String BACKUP_FOLDER = ".update.old";
    private final Path backupRoot;
    private final Path serverRoot;
    private final Path candidateRoot;

    /**
     * create a record for server at {@code serverRoot}. The recorded files will be stored in {@tempRoot}
     *
     * @param serverRoot - root folder of the server that will be updated
     */
    ApplyStageBackup(Path serverRoot, Path candidateRoot) throws IOException {

        this.serverRoot = serverRoot;
        this.candidateRoot = candidateRoot;
        this.backupRoot = serverRoot.resolve(BACKUP_FOLDER);

        if (ProsperoLogger.ROOT_LOGGER.isDebugEnabled()) {
            ProsperoLogger.ROOT_LOGGER.debug("Creating backup record in " + backupRoot);
        }

        if (!Files.exists(backupRoot)) {
            if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
                ProsperoLogger.ROOT_LOGGER.trace("Creating backup directory in " + backupRoot);
            }

            Files.createDirectories(backupRoot);
        } else if (!Files.isDirectory(backupRoot) || !Files.isWritable(backupRoot)) {
            throw new RuntimeException(String.format(
                    "Unable to create backup in %s. It is not a directory or is not writable.",
                    backupRoot
            ));
        }

        final File[] files = backupRoot.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                if (ProsperoLogger.ROOT_LOGGER.isTraceEnabled()) {
                    ProsperoLogger.ROOT_LOGGER.trace("Removing existing backup files: " + file);
                }

                FileUtils.forceDelete(file);
            }
        }
    }

    /**
     * add all the files in the server to cache
     *
     * @throws IOException - if unable to backup the files
     */
    public void recordAll() throws IOException {
        Files.walkFileTree(serverRoot, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                // if the file in the installation is not readable AND it does not exist in the candidate
                // we assume it is user controlled file and we ignore it
                return ignoreUserManagedFiles(file, exc);
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

                if (dir.equals(backupRoot)) {
                    return FileVisitResult.SKIP_SUBTREE;
                } else {
                    final Path relative = serverRoot.relativize(dir);
                    Files.createDirectories(backupRoot.resolve(relative));
                    return FileVisitResult.CONTINUE;
                }
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isUserProtectedFile(file)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                final Path relative = serverRoot.relativize(file);
                if (relative.startsWith(Path.of(".installation", ".git"))) {
                    // when using hardlinks, we need to remove the file and copy the new one in it's place otherwise both would be changed.
                    // the git folder is manipulated by jgit and we can't control how it operates on the files
                    // therefore we need to copy the files upfront rather than hardlinking them
                    Files.copy(file, backupRoot.resolve(relative));
                } else {
                    // we try to use hardlinks instead of copy to save disk space
                    // fallback on copy if Filesystem doesn't support hardlinks
                    try {
                        Files.createLink(backupRoot.resolve(relative), file);
                    } catch (UnsupportedOperationException e) {
                        Files.copy(file, backupRoot.resolve(relative));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * clean up the cache
     */
    @Override
    public void close() {
        FileUtils.deleteQuietly(backupRoot.toFile());
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

        if (!Files.exists(backupRoot)) {
            throw new RuntimeException("Backup root doesn't exist.");
        }

        // copy backed-up files back into the server
        Files.walkFileTree(backupRoot, restoreModifiedFiles());

        // remove all files added to recorded folders that were not handled by addedFiles
        Files.walkFileTree(serverRoot, deleteNewFiles());
    }

    private SimpleFileVisitor<Path> deleteNewFiles() {
        return new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return ignoreUserManagedFiles(file, exc);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isUserProtectedFile(file)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
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

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(backupRoot)) {
                    return FileVisitResult.SKIP_SUBTREE;
                } else {
                    return FileVisitResult.CONTINUE;
                }
            }
        };
    }

    private FileVisitResult ignoreUserManagedFiles(Path file, IOException exc) throws IOException {
        // if the file in the installation is not readable AND it does not exist in the candidate
        // we assume it is user controlled file and we ignore it
        if (isUserProtectedFile(file)) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        throw exc;
    }

    private boolean isUserProtectedFile(Path file) {
        final Path relative = serverRoot.relativize(file);
        final Path candidatePath = candidateRoot.resolve(relative);
        return !Files.isReadable(file) && !Files.exists(candidatePath);
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
