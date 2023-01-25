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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/*
 * Taken from https://github.com/wildfly/galleon/tree/1fa07afb29b2ec61222e746b107a6822e836d05c/core/src/test/java/org/jboss/galleon/test/util/fs/state
 * @author Alexey Loubyansky
 */
public abstract class PathState {

    public abstract static class Builder {

        protected final String name;

        protected Builder(String name) {
            this.name = name;
        }

        public abstract PathState build();
    }

    protected final String name;

    protected PathState(String name) {
        this.name = name;
    }

    protected void assertState(Path parent) {
        if (name != null) {
            final Path path = parent.resolve(name);
            if (!Files.exists(path)) {
                Assert.fail("Path doesn't exist: " + path);
            }
            doAssertState(path);
        } else {
            doAssertState(parent);
        }
    }

    protected abstract void doAssertState(Path path);

    protected static String read(Path p) {
        final StringWriter strWriter = new StringWriter();
        try(BufferedReader reader = Files.newBufferedReader(p);
            BufferedWriter writer = new BufferedWriter(strWriter)) {
            String line = reader.readLine();
            if (line != null) {
                writer.write(line);
                line = reader.readLine();
                while (line != null) {
                    writer.newLine();
                    writer.write(line);
                    line = reader.readLine();
                }
                writer.flush();
                return strWriter.getBuffer().toString();
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + p, e);
        }
    }
}
