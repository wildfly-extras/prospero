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

package org.wildfly.prospero.cli;

import org.jboss.galleon.diff.FsDiff;
import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.FileConflict;

import java.util.List;

import static org.wildfly.prospero.api.FileConflict.Change.MODIFIED;
import static org.wildfly.prospero.api.FileConflict.Change.REMOVED;
import static org.wildfly.prospero.api.FileConflict.Resolution.UPDATE;

public class FileConflictPrinter {

    public static void print(List<FileConflict> fileConflicts, Console console) {
        if (!fileConflicts.isEmpty()) {
            console.println("\n");
            console.println(CliMessages.MESSAGES.conflictingChangesDetected());
            for (FileConflict fileConflict : fileConflicts) {
                console.println(FileConflictPrinter.toString(fileConflict));
            }
            console.println("\n");
        }
    }

    public static String toString(FileConflict conflict) {
        String status;
        if (conflict.getResolution() == UPDATE) {
            status = "!" + FsDiff.FORCED;
        } else {
            if (conflict.getUserChange() == conflict.getUpdateChange()) {
                status = "!" + FsDiff.CONFLICT;
            } else if (conflict.getUserChange() == MODIFIED && conflict.getUpdateChange() == REMOVED) {
                status = "!" + FsDiff.MODIFIED;
            } else {
                switch (conflict.getUserChange()) {
                    case MODIFIED:
                        status = " " + FsDiff.MODIFIED;
                        break;
                    case ADDED:
                        status = " " + FsDiff.ADDED;
                        break;
                    case REMOVED:
                        status = " " + FsDiff.REMOVED;
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected Change " + conflict);
                }
            }
        }
        return status + " " + conflict.getRelativePath();
    }
}
