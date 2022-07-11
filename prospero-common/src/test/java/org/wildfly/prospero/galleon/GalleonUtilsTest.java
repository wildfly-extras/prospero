/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.galleon;

import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.*;

public class GalleonUtilsTest {

    @Test
    public void clearAndRestoreJbossModuleProperty() throws Exception {
        System.setProperty(GalleonUtils.MODULE_PATH_PROPERTY, "foo");
        try {
            GalleonUtils.executeGalleon((options) -> assertNull(System.getProperty(GalleonUtils.MODULE_PATH_PROPERTY)), Paths.get("test"));
            assertEquals(System.getProperty(GalleonUtils.MODULE_PATH_PROPERTY), "foo");
        } finally {
            System.clearProperty(GalleonUtils.MODULE_PATH_PROPERTY);
        }
    }

    @Test
    public void setForkModeOptionBeforeExecution() throws Exception {
        GalleonUtils.executeGalleon((options) -> assertEquals(options.get(GalleonUtils.JBOSS_FORK_EMBEDDED_PROPERTY), GalleonUtils.JBOSS_FORK_EMBEDDED_VALUE),
                Paths.get("test"));
    }

    @Test
    public void setAndClearMavenRepoPropertyAroundExecution() throws Exception {
        GalleonUtils.executeGalleon((options) -> assertEquals(System.getProperty(GalleonUtils.MAVEN_REPO_LOCAL), "test"),
                Paths.get("test"));
    }
}