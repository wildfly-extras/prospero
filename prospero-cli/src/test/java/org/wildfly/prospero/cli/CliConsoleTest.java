/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.cli;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.prospero.api.ArtifactChange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CliConsoleTest extends AbstractConsoleTest {
    private CliConsole cliConsole;

    private ByteArrayOutputStream outputStream;

    @Before
    public void setUp() {
        cliConsole = new CliConsole();
        outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));
    }

    @Test
    public void testUpdatesFoundWithUpdates_ArtifactChange_update() {
        List<ArtifactChange> artifactChanges = new ArrayList<>();
        ArtifactChange artifactChange = ArtifactChange.updated(new DefaultArtifact("test.group", "test-artifact2", "jar", "2.0.0"), new DefaultArtifact("test.group", "test-artifact2", "jar", "2.1.0"), "channel-1");

        artifactChanges.add(artifactChange);
        cliConsole.updatesFound(artifactChanges);
        String capturedOutput = outputStream.toString();

        assertThat(capturedOutput.contains(String.format(
                "  %s%-50s    %-20s ==>  %-20s ==> %-20s%n",
                "",
                artifactChange.getArtifactName(),
                artifactChange.getOldVersion().orElse("[]"),
                artifactChange.getNewVersion().orElse("[]"),
                artifactChange.getChannelName()
        )));
    }
    @Test
    public void testUpdatesFoundWithUpdates_ArtifactChange_add() {
        List<ArtifactChange> artifactChanges = new ArrayList<>();
        ArtifactChange artifactChange = ArtifactChange.added(new DefaultArtifact("test.group", "test-artifact2", "jar", "2.0.0"), "channel-1");

        artifactChanges.add(artifactChange);
        cliConsole.updatesFound(artifactChanges);
        String capturedOutput = outputStream.toString();

        assertThat(capturedOutput.contains(String.format(
                "  %s%-50s    %-20s ==>  %-20s ==> %-20s%n",
                "",
                artifactChange.getArtifactName(),
                artifactChange.getOldVersion().orElse("[]"),
                artifactChange.getNewVersion().orElse("[]"),
                artifactChange.getChannelName()
        )));
    }

    @After
    public void destory() throws IOException {
        outputStream.close();
        cliConsole = null;
    }
}
