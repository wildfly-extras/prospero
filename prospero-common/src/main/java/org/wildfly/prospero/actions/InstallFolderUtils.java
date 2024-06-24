/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

import org.wildfly.prospero.ProsperoLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class InstallFolderUtils {

    static void verifyIsWritable(Path directory) {
        if (!isWritable(directory)) {
            throw ProsperoLogger.ROOT_LOGGER.dirMustBeWritable(directory);
        }
    }

    static void verifyIsEmptyDir(Path directory) {
        if (directory.toFile().isFile()) {
            // file exists and is a regular file
            throw ProsperoLogger.ROOT_LOGGER.dirMustBeDirectory(directory);
        }
        if (!isEmptyDirectory(directory)) {
            throw ProsperoLogger.ROOT_LOGGER.cannotInstallIntoNonEmptyDirectory(directory);
        }
    }

    /**
     * convert the {@code symlink} to a real path. If any of the parent folders are a symlink, they will be
     * converted to real path as well.
     *
     * @param symlink
     * @return
     */
    static Path toRealPath(Path symlink) {
        /*
         * There's an issue when trying to copy artifacts to a folder that is symlink
         * This only happens when the last segment of path is symlink itself, but to be consistent, we replace the
         * symlink anywhere in the path. This way we know we're always operating on a real path from this point on.
         */
        Path path = symlink;

        // find a symlink (if any) in the path and its parents
        while (path != null && !(Files.exists(path) && Files.isSymbolicLink(path))) {
            path = path.getParent();
        }

        // if no symlinks were found we got to the root of the path (null) and we don't need to anything
        if (path == null) {
            return symlink;
        } else {
            // get the subfolder path between the symlink and the actual path
            final Path relativized = path.relativize(symlink);
            try {
                // evaluate the symlink and append the relative path to get back to the starting folder
                return path.toRealPath().resolve(relativized);
            } catch (IOException e) {
                // we know the file at path does exist, so if we got an exception here, that's an I/O error
                throw ProsperoLogger.ROOT_LOGGER.unableToEvaluateSymbolicLink(symlink, e);
            }
        }
    }

    private static boolean isWritable(final Path path) {
        Path absPath = path.toAbsolutePath();
        if (Files.exists(absPath)) {
            return Files.isWritable(absPath);
        } else {
            if (absPath.getParent() == null) {
                return false;
            } else {
                return isWritable(absPath.getParent());
            }
        }
    }

    private static boolean isEmptyDirectory(Path directory) {
        String[] list = directory.toFile().list();
        return list == null || list.length == 0;
    }
}
