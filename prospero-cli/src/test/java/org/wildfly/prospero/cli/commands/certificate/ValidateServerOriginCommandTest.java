/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.prospero.cli.commands.certificate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.wildfly.channel.ChannelManifest;
import org.wildfly.channel.spi.SignatureResult;
import org.wildfly.prospero.actions.CertificateAction;
import org.wildfly.prospero.actions.VerificationResult;
import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.cli.ActionFactory;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.ReturnCodes;
import org.wildfly.prospero.cli.commands.AbstractMavenCommandTest;
import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.test.MetadataTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class ValidateServerOriginCommandTest extends AbstractMavenCommandTest {

    @Mock
    private ActionFactory actionFactory;

    @Mock
    private CertificateAction certificateAction;

    @Captor
    private ArgumentCaptor<MavenOptions> mavenOptions;

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Path dir;

    @Override
    protected ActionFactory createActionFactory() {
        return actionFactory;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        when(actionFactory.certificateAction(any())).thenReturn(certificateAction);

        this.dir = tempDir.newFolder().toPath();
        MetadataTestUtils.createInstallationMetadata(dir, new ChannelManifest(null, null, null, null),
                Collections.emptyList());
        MetadataTestUtils.createGalleonProvisionedState(dir);
    }

    @Test
    public void printPositiveResultWhenNoInvalidComponents() throws Exception {
        when(certificateAction.verifyServerOrigin(any(), any())).thenReturn(new VerificationResult(
           Collections.emptyList(),
           Collections.emptyList(),
           Collections.emptySet()
        ));

        int exitCode = commandLine.execute(getDefaultArguments());

        Assert.assertEquals(ReturnCodes.SUCCESS, exitCode);

        Assertions.assertThat(getStandardOutput())
                .contains(CliMessages.MESSAGES.verifiedComponentsOnly());
    }

    @Test
    public void printListOfInvalidArtifactsIfPresent() throws Exception {
        when(certificateAction.verifyServerOrigin(any(), any())).thenReturn(new VerificationResult(
                List.of(new VerificationResult.InvalidBinary(Path.of("test.jar"), "test:test", SignatureResult.Result.INVALID)),
                Collections.emptyList(),
                Collections.emptySet()
        ));

        int exitCode = commandLine.execute(getDefaultArguments());

        Assert.assertEquals(ReturnCodes.PROCESSING_ERROR, exitCode);

        Assertions.assertThat(getStandardOutput())
                .contains(CliMessages.MESSAGES.unverifiedComponentsListHeader())
                .contains("  * test.jar : " + CliMessages.MESSAGES.componentInvalidLocalFile() + "\n");
    }

    @Override
    protected void doLocalMock() throws Exception {
        Mockito.when(certificateAction.verifyServerOrigin(any(), any())).thenReturn(new VerificationResult(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptySet()
        ));
    }

    @Override
    protected MavenOptions getCapturedMavenOptions() throws Exception {
        Mockito.verify(certificateAction).verifyServerOrigin(any(), mavenOptions.capture());
        return mavenOptions.getValue();
    }

    @Override
    protected String[] getDefaultArguments() {
        return new String[]{CliConstants.Commands.CERTIFICATE, CliConstants.Commands.VALIDATE_SERVER, CliConstants.DIR, dir.toAbsolutePath().toString()};
    }
}