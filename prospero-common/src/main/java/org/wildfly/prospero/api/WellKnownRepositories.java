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

package org.wildfly.prospero.api;

import java.util.function.Supplier;

import org.eclipse.aether.repository.RemoteRepository;

/**
 * Enumeration defining well known Maven repositories.
 */
public enum WellKnownRepositories implements Supplier<RemoteRepository> {

    MRRC(new RemoteRepository.Builder("mrrc", "default", "https://maven.repository.redhat.com/ga/")
            .build()),

    CENTRAL(new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/")
            .build()),

    JBOSS_PUBLIC(new RemoteRepository.Builder("jboss-public", "default", "https://repository.jboss.org/nexus/content/groups/public/")
            .build());

    private final RemoteRepository repository;

    private WellKnownRepositories(RemoteRepository repository) {
        this.repository = repository;
    }

    @Override
    public RemoteRepository get() {
        return repository;
    }
}
