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

package org.wildfly.prospero.cli.commands.options;

import org.wildfly.prospero.cli.commands.CliConstants;
import org.wildfly.prospero.wfchannel.MavenSessionManager;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Optional;

public class LocalRepoOptions {
    @CommandLine.Option(
            names = CliConstants.LOCAL_REPO,
            paramLabel = CliConstants.PATH,
            order = 6
    )
    Path localRepo;

    @CommandLine.Option(
            names = CliConstants.NO_LOCAL_MAVEN_CACHE,
            order = 7
    )
    boolean noLocalCache;

    public static Optional<Path> getLocalRepo(LocalRepoOptions localRepoParam) {
        if (localRepoParam == null) {
            return Optional.of(MavenSessionManager.LOCAL_MAVEN_REPO);
        } else if (localRepoParam.noLocalCache) {
            return Optional.empty();
        } else {
            return Optional.of(localRepoParam.localRepo);
        }
    }
}
