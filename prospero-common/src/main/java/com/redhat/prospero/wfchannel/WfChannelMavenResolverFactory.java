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

package com.redhat.prospero.wfchannel;

import org.jboss.galleon.ProvisioningException;
import org.wildfly.channel.MavenRepository;
import org.wildfly.channel.spi.MavenVersionsResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class WfChannelMavenResolverFactory implements MavenVersionsResolver.Factory {

    private final Path provisioningRepo;

    public WfChannelMavenResolverFactory() throws ProvisioningException {
        try {
            provisioningRepo = Files.createTempDirectory("provisioning-repo");
            provisioningRepo.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new ProvisioningException(e);
        }
    }

    @Override
    public MavenVersionsResolver create(List<MavenRepository> mavenRepositories, boolean resolveLocalCache) {
        return new WfChannelMavenResolver(mavenRepositories, resolveLocalCache, provisioningRepo);
    }

    public Path getProvisioningRepo() {
        return provisioningRepo;
    }
}
