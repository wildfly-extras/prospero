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

package org.wildfly.prospero.utils.filestate;

import org.junit.Assert;

import java.nio.file.Files;
import java.nio.file.Path;

/*
 * Taken from https://github.com/wildfly/galleon/tree/1fa07afb29b2ec61222e746b107a6822e836d05c/core/src/test/java/org/jboss/galleon/test/util/fs/state
 */
public class FileContentState extends PathState {

    public static class FileContentBuilder extends PathState.Builder {

        private final String content;

        private FileContentBuilder(String name, String content) {
            super(name);
            this.content = content;
        }

        public FileContentState build() {
            return new FileContentState(name, content);
        }
    }

    public static FileContentBuilder builder(String name, String content) {
        return new FileContentBuilder(name, content);
    }

    private final String content;

    protected FileContentState(String name, String content) {
        super(name);
        this.content = content;
    }

    /* (non-Javadoc)
     * @see org.jboss.galleon.test.util.pathstate.PathState#doAssertState(java.nio.file.Path)
     */
    @Override
    protected void doAssertState(Path path) {
        if(Files.isDirectory(path)) {
            Assert.fail("Path is a not directory: " + path);
        }
        if(!content.equals(read(path))) {
            Assert.fail(path + " expected to contain " + content + " instead of " + read(path));
        }
    }
}
