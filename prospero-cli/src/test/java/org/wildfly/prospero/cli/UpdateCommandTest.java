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

package org.wildfly.prospero.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.galleon.Constants;
import org.jboss.galleon.state.ProvisionedFeaturePack;
import org.jboss.galleon.state.ProvisionedState;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.xml.ProvisionedStateXmlWriter;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.prospero.actions.Update;
import org.wildfly.prospero.cli.commands.UpdateCommand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UpdateCommandTest extends AbstractConsoleTest {

    public static final String A_PROSPERO_FP = UpdateCommand.PROSPERO_FP_GA + ":1.0.0";
    public static final String OTHER_FP = "com.another:galleon-pack:1.0.0";
    public static final Path GALLEON_PROVISIONED_STATE_FILE = Paths.get(Constants.PROVISIONED_STATE_DIR, Constants.PROVISIONED_STATE_XML);
    public static final String MODULES_DIR = "modules";

    @Mock
    private Update update;

    @Mock
    private ActionFactory actionFactory;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Override
    protected ActionFactory createActionFactory() {
        return actionFactory;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        when(actionFactory.update(any(), any(), any())).thenReturn(update);
    }

    @After
    public void tearDown() {
        System.clearProperty(UpdateCommand.JBOSS_MODULE_PATH);
    }

    @Test
    public void errorIfTargetPathNotPresent() {
        int exitCode = commandLine.execute("update");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(UpdateCommand.DIR_OR_SELF_IS_MANDATORY));
    }

    @Test
    public void callUpdate() throws Exception {
        int exitCode = commandLine.execute("update", "--dir", "test");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(update).doUpdateAll();
    }

    @Test
    public void selfUpdateRequiresModulePathProp() {
        int exitCode = commandLine.execute("update", "--self");

        assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);
        assertTrue(getErrorOutput().contains(Messages.unableToLocateInstallation()));
    }

    @Test
    public void selfUpdatePassesModulePathAsDir() throws Exception {
        final Path baseDir = mockGalleonInstallation(A_PROSPERO_FP);
        System.setProperty(UpdateCommand.JBOSS_MODULE_PATH, baseDir.resolve(MODULES_DIR).toString());
        int exitCode = commandLine.execute("update", "--self");

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(actionFactory).update(eq(baseDir.toAbsolutePath()), any(), any());
        Mockito.verify(update).doUpdateAll();
    }

    @Test
    public void dirParameterOverridesModulePathInSelfUpdate() throws Exception {
        final Path baseDir = mockGalleonInstallation(A_PROSPERO_FP);
        System.setProperty(UpdateCommand.JBOSS_MODULE_PATH, "test");
        int exitCode = commandLine.execute("update", "--self", "--dir", baseDir.toAbsolutePath().toString());

        assertEquals(ReturnCodes.SUCCESS, exitCode);
        Mockito.verify(actionFactory).update(eq(baseDir.toAbsolutePath()), any(), any());
        Mockito.verify(update).doUpdateAll();
    }

    @Test
    public void selfUpdateFailsIfMultipleFPsDetected() throws Exception {
        final Path baseDir = mockGalleonInstallation(A_PROSPERO_FP, OTHER_FP);
        int exitCode = commandLine.execute("update", "--self", "--dir", baseDir.toAbsolutePath().toString());

        assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);
        assertTrue(getErrorOutput().contains(Messages.unexpectedPackageInSelfUpdate(baseDir.toAbsolutePath().toString())));
    }

    @Test
    public void selfUpdateFailsIfProsperoFPNotDetected() throws Exception {
        final Path baseDir = mockGalleonInstallation(OTHER_FP);
        int exitCode = commandLine.execute("update", "--self", "--dir", baseDir.toAbsolutePath().toString());

        assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);
        assertTrue(getErrorOutput().contains(Messages.unexpectedPackageInSelfUpdate(baseDir.toAbsolutePath().toString())));
    }

    @Test
    public void offlineModeRequiresLocalRepoOption() throws Exception {
        final Path baseDir = mockGalleonInstallation(A_PROSPERO_FP);
        int exitCode = commandLine.execute("update", "--self", "--dir", baseDir.toAbsolutePath().toString(),
                "--offline");

        assertEquals(ReturnCodes.INVALID_ARGUMENTS, exitCode);
        assertTrue(getErrorOutput().contains(Messages.offlineModeRequiresLocalRepo()));
    }

    private Path mockGalleonInstallation(String... fps) throws IOException, javax.xml.stream.XMLStreamException {
        final ProvisionedState.Builder builder = ProvisionedState.builder();
        for (String fp : fps) {
            builder.addFeaturePack(ProvisionedFeaturePack.builder(FeaturePackLocation.fromString(fp).getFPID()).build());
        }
        ProvisionedState state = builder.build();
        final File baseDir = tempFolder.newFolder();
        ProvisionedStateXmlWriter.getInstance().write(state, baseDir.toPath().resolve(GALLEON_PROVISIONED_STATE_FILE));
        return baseDir.toPath();
    }
}
