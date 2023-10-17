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
