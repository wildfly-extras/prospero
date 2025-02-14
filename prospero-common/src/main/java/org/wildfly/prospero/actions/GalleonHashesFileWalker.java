package org.wildfly.prospero.actions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.diff.FsDiff;
import org.jboss.galleon.diff.FsEntry;
import org.wildfly.prospero.api.exceptions.OperationException;
import org.wildfly.prospero.galleon.GalleonUtils;

/**
 * Traverse file tree based on Galleon hashes record.
 * The hashes directory reflects the same directory structure as the server. The files in each directory are
 * recorded in a hashes file in those directories.
 *
 * We use Galleon APIs to parse the hashes
 */
abstract class GalleonHashesFileWalker {

    private final Path rootPath;

    GalleonHashesFileWalker(Path rootPath) {
        this.rootPath = rootPath;
    }

    public void walk() throws IOException {
        try {
            final FsDiff changes = GalleonUtils.findChanges(rootPath);
            doWalk(changes.getOriginalRoot());

        } catch (ProvisioningException e) {
            throw new RuntimeException(e);
        } catch (OperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void doWalk(FsEntry root) throws IOException {
        for (FsEntry child : root.getChildren()) {
            if (child.isDiffStatusAdded()) {
                // ignore added files and folders
                continue;
            }

            if (Files.isDirectory(rootPath.resolve(child.getRelativePath()))) {
                visitDirectory(Path.of(child.getRelativePath()));
                doWalk(child);
            } else {
                visitFile(Path.of(child.getRelativePath()));
            }
        }
    }

    abstract void visitFile(Path file) throws IOException;

    abstract void visitDirectory(Path relative) throws IOException;
}
