package org.wildfly.prospero.it.cli;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.it.ExecutionUtils;
import org.wildfly.prospero.test.MetadataTestUtils;

public class InstallTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private File targetDir;

    @Before
    public void setUp() throws IOException {
        targetDir = tempDir.newFolder();
    }

    @Test
    public void testInstallWithProvisionConfig() throws Exception {
        URL provisionConfig = MetadataTestUtils.prepareProvisionConfigAsUrl("channels/wfcore-19-base.yaml");

        ExecutionUtils.prosperoExecution(CliConstants.INSTALL,
                        CliConstants.PROVISION_CONFIG, provisionConfig.getPath(),
                        CliConstants.FPL, "wildfly-core@maven(org.jboss.universe:community-universe):19.0",
                        CliConstants.REMOTE_REPOSITORIES,
                        "https://repo1.maven.org/maven2/,https://repository.jboss.org/nexus/content/groups/public-jboss",
                        CliConstants.DIR, targetDir.getAbsolutePath())
                .withTimeLimit(10, TimeUnit.MINUTES)
                .execute()
                .assertReturnCode(ReturnCodes.SUCCESS);
    }
}
