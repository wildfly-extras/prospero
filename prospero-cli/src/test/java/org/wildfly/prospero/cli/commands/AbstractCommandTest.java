package org.wildfly.prospero.cli.commands;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.cli.ArgumentParsingException;
import org.wildfly.prospero.test.MetadataTestUtils;
import java.nio.file.Path;
import java.util.Optional;

public class AbstractCommandTest {

    public static final String A_PROSPERO_FP = UpdateCommand.PROSPERO_FP_GA + ":1.0.0";

    private Path installationDir;
    private Path invalidDir;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() throws Exception {

        installationDir = tempFolder.newFolder().toPath();
        MetadataTestUtils.createInstallationMetadata(installationDir);
        MetadataTestUtils.createGalleonProvisionedState(installationDir, A_PROSPERO_FP);

        invalidDir = tempFolder.newFolder().toPath();
    }

    @Test
    public void testValidInstallationDir() throws Exception {
        Assert.assertEquals(installationDir, AbstractCommand.determineInstallationDirectory(Optional.empty(), installationDir));
    }

    @Test
    public void testValidInstallationSubdirectory() throws Exception {
        Assert.assertEquals(installationDir, AbstractCommand.determineInstallationDirectory(Optional.empty(), installationDir.resolve("bin")));
    }

    @Test(expected = ArgumentParsingException.class)
    public void testInvalidInstallationDir() throws Exception {
        AbstractCommand.determineInstallationDirectory(Optional.empty(), invalidDir);
    }
}
