package org.wildfly.prospero.actions;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.jboss.galleon.Constants;

/**
 * Traverse file tree based on Galleon hashes record.
 * The hashes directory reflects the same directory structure as the server. The files in each directory are
 * recorded in a hashes file in those directories.
 *
 * To traverse the file tree, we need to find hashes files and list the files in them.
 */
abstract class GalleonHashesFileVisitor extends SimpleFileVisitor<Path> {

    private final Path hashesRoot;
    private final Path backupRoot;

    GalleonHashesFileVisitor(Path hashesRoot, Path backupRoot) {
        this.hashesRoot = hashesRoot;
        this.backupRoot = backupRoot;
    }

    abstract FileVisitResult doVisitFile(Path file) throws IOException;

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (file.getFileName().toString().equals(Constants.HASHES)) {
            final Path currentDirectory = hashesRoot.relativize(file.getParent());
            // read hashes descriptor
            final List<String> readLines = FileUtils.readLines(file.toFile());

            // read every other line - we don't care about hashes atm.
            for (int i = 0; i < readLines.size(); i+=2) {
                final String recordedFileName = readLines.get(i);

                doVisitFile(currentDirectory.resolve(recordedFileName));
            }
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        if (dir.equals(backupRoot)) {
            return FileVisitResult.SKIP_SUBTREE;
        } else {
            final Path relative = hashesRoot.relativize(dir);
            return doPreVisitDirectory(relative);
        }
    }

    protected FileVisitResult doPreVisitDirectory(Path relative) throws IOException {
        return FileVisitResult.CONTINUE;
    }
}
