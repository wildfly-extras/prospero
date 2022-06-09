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