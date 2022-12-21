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

import java.nio.file.Path;

/*
 * Taken from https://github.com/wildfly/galleon/tree/1fa07afb29b2ec61222e746b107a6822e836d05c/core/src/test/java/org/jboss/galleon/test/util/fs/state
 */
public class SkipPathState extends PathState {

    public static class SkipPathBuilder extends PathState.Builder {

        private SkipPathBuilder(String name) {
            super(name);
        }

        public SkipPathState build() {
            return new SkipPathState(name);
        }
    }

    public static SkipPathBuilder builder(String name) {
        return new SkipPathBuilder(name);
    }

    protected SkipPathState(String name) {
        super(name);
    }

    /* (non-Javadoc)
     * @see org.jboss.galleon.test.util.pathstate.PathState#doAssertState(java.nio.file.Path)
     */
    @Override
    protected void doAssertState(Path path) {
    }
}
