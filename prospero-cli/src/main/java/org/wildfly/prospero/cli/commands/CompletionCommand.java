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

package org.wildfly.prospero.cli.commands;

import org.wildfly.prospero.cli.DistributionInfo;
import picocli.AutoComplete;
import picocli.CommandLine;

/**
 * By invoking this command, user can obtain a shell completion script.
 *
 * User can apply the completion script by running `source <(prospero completion)` (in bash), or can redirect the
 * output of this command to a file.
 *
 * The completion script can be used for bash and zsh.
 */
@CommandLine.Command(
        name = "completion",
        mixinStandardHelpOptions = true,
        helpCommand = true)
public class CompletionCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    public void run() {
        String script = AutoComplete.bash(DistributionInfo.DIST_NAME, spec.root().commandLine());
        // not PrintWriter.println: scripts with Windows line separators fail in strange ways!
        spec.commandLine().getOut().print(script);
        spec.commandLine().getOut().print('\n');
        spec.commandLine().getOut().flush();
    }
}
