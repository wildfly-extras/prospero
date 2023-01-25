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

package org.wildfly.prospero.galleon;

import org.jboss.galleon.ProvisioningException;
import org.junit.Test;
import org.wildfly.channel.UnresolvedMavenArtifactException;

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

    @Test
    public void clearAndRestoreMBeanBuilderProperty() throws Exception {
        System.setProperty(GalleonUtils.JAVAX_MANAGEMENT_BUILDER_INITIAL_PROPERTY, "foo");
        try {
            GalleonUtils.executeGalleon((options) -> assertNull(System.getProperty(GalleonUtils.JAVAX_MANAGEMENT_BUILDER_INITIAL_PROPERTY)), Paths.get("test"));
            assertEquals(System.getProperty(GalleonUtils.JAVAX_MANAGEMENT_BUILDER_INITIAL_PROPERTY), "foo");
        } finally {
            System.clearProperty(GalleonUtils.JAVAX_MANAGEMENT_BUILDER_INITIAL_PROPERTY);
        }
    }

    @Test
    public void extractFailedArtifactResolutionOnCopy() throws Exception {
        final ProvisioningException test = new ProvisioningException(
                new UnresolvedMavenArtifactException("test"));
        try {
            GalleonUtils.executeGalleon(o -> {
                throw test;
            }, Paths.get("test"));
            fail("Should throw an exception");
        } catch (UnresolvedMavenArtifactException e) {
            assertEquals("test", e.getMessage());
        }
    }
}