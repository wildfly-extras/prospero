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

import org.wildfly.prospero.api.Console;
import org.wildfly.prospero.api.FileConflict;

import java.util.List;

public class FileConflictPrinter {

    public static void print(List<FileConflict> fileConflicts, Console console) {
        if (!fileConflicts.isEmpty()) {
            console.println("\n");
            console.println(CliMessages.MESSAGES.conflictingChangesDetected());
            for (FileConflict fileConflict : fileConflicts) {
                console.println(fileConflict.prettyPrint());
            }
            console.println("\n");
        }
    }
}
