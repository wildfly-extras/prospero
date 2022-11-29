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

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.wildfly.channel.Repository;
import org.wildfly.channel.maven.VersionResolverFactory;
import org.wildfly.channel.spi.MavenVersionsResolver;

import java.nio.file.Path;
import java.util.Collection;

public class CachedVersionResolverFactory implements MavenVersionsResolver.Factory {

    private final VersionResolverFactory factory;
    private final Path installDir;
    private final RepositorySystem system;
    private final DefaultRepositorySystemSession session;

    public CachedVersionResolverFactory(VersionResolverFactory factory, Path installDir, RepositorySystem system, DefaultRepositorySystemSession session) {
        this.factory = factory;
        this.installDir = installDir;
        this.system = system;
        this.session = session;
    }

    @Override
    public MavenVersionsResolver create(Collection<Repository> repositories) {
        return new CachedVersionResolver(factory.create(repositories), installDir, system, session);
    }

}
