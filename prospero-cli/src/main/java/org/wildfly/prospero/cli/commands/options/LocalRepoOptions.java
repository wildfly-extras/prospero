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

package org.wildfly.prospero.cli.commands.options;

import org.wildfly.prospero.api.MavenOptions;
import org.wildfly.prospero.cli.ArgumentParsingException;
import org.wildfly.prospero.cli.CliMessages;
import org.wildfly.prospero.cli.commands.CliConstants;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class LocalRepoOptions {
    @CommandLine.Option(
            names = CliConstants.LOCAL_CACHE,
            paramLabel = CliConstants.PATH,
            order = 6
    )
    Path localMavenCache;

    @Deprecated
    @CommandLine.Option(
            names = CliConstants.NO_LOCAL_MAVEN_CACHE,
            order = 7
    )
    Optional<Boolean> noLocalCache = Optional.empty();

    @CommandLine.Option(
            names = CliConstants.USE_LOCAL_MAVEN_CACHE,
            order = 8
    )
    Optional<Boolean> useLocalCache = Optional.empty();

    public MavenOptions.Builder toOptions() throws ArgumentParsingException {
        final MavenOptions.Builder builder = MavenOptions.builder();

        useLocalCache.ifPresent(useLocalCache -> builder.setNoLocalCache(!useLocalCache));

        if (noLocalCache.isPresent()) {
            System.out.println("WARNING: " + CliConstants.NO_LOCAL_MAVEN_CACHE + " is deprecated. Please use " + CliConstants.USE_LOCAL_MAVEN_CACHE + " instead.");
            builder.setNoLocalCache(noLocalCache.get());
        }
        if (localMavenCache != null) {
            if (Files.exists(this.localMavenCache) && !Files.isDirectory(this.localMavenCache)) {
                throw CliMessages.MESSAGES.repositoryIsNotDirectory(this.localMavenCache);
            }
            builder.setLocalCachePath(localMavenCache.toAbsolutePath());
        }
        return builder;
    }
}
