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

import org.junit.Test;
import org.wildfly.prospero.api.FileConflict;

import static org.junit.Assert.assertEquals;
import static org.wildfly.prospero.api.FileConflict.Change.ADDED;
import static org.wildfly.prospero.api.FileConflict.Change.MODIFIED;
import static org.wildfly.prospero.api.FileConflict.Change.NONE;
import static org.wildfly.prospero.api.FileConflict.Change.REMOVED;
import static org.wildfly.prospero.api.FileConflict.Resolution.USER;

public class FileChangePrinterTest {

    @Test
    public void testFileAddedByUser() {
        // can't be modified or removed by update, otherwise user could not add it
        final FileConflict c = new FileConflict(ADDED, ADDED, USER, "foo.txt");
        assertEquals("!C foo.txt", FileConflictPrinter.toString(c));

        final FileConflict c1 = new FileConflict(ADDED, NONE, USER, "foo.txt");
        assertEquals(" + foo.txt", FileConflictPrinter.toString(c1));
    }

    @Test
    public void testFileModifiedByUser() {
        // can't be added by update, otherwise user wouldn't be able to modify it
        final FileConflict c = new FileConflict(MODIFIED, MODIFIED, USER, "foo.txt");
        assertEquals("!C foo.txt", FileConflictPrinter.toString(c));

        final FileConflict c1 = new FileConflict(MODIFIED, REMOVED, USER, "foo.txt");
        assertEquals("!M foo.txt", FileConflictPrinter.toString(c1));

        final FileConflict c2 = new FileConflict(MODIFIED, NONE, USER, "foo.txt");
        assertEquals(" M foo.txt", FileConflictPrinter.toString(c2));
    }

    @Test
    public void testFileRemovedByUser() {
        // can't be added by update, otherwise user wouldn't have anything to remove
        // can't be removed by update, because there's no change there
        final FileConflict c1 = new FileConflict(REMOVED, MODIFIED, USER, "foo.txt");
        assertEquals(" - foo.txt", FileConflictPrinter.toString(c1));

        final FileConflict c2 = new FileConflict(REMOVED, NONE, USER, "foo.txt");
        assertEquals(" - foo.txt", FileConflictPrinter.toString(c2));
    }

}