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

package integration;

import org.wildfly.prospero.api.ProvisioningDefinition;
import org.eclipse.aether.repository.RemoteRepository;

import java.util.Arrays;
import java.util.List;

public class WfCoreTestBase {

    protected final List<RemoteRepository> repositories = defaultRemoteRepositories();

    protected ProvisioningDefinition.Builder defaultWfCoreDefinition() {
        return ProvisioningDefinition.builder()
                .setFpl("wildfly-core@maven(org.jboss.universe:community-universe):17.0")
                .setRepositories(repositories);
    }

    public static List<RemoteRepository> defaultRemoteRepositories() {
        return Arrays.asList(
                new RemoteRepository.Builder("maven-central", "default", "https://repo1.maven.org/maven2/").build(),
                new RemoteRepository.Builder("nexus", "default", "https://repository.jboss.org/nexus/content/groups/public-jboss").build(),
                new RemoteRepository.Builder("maven-redhat-ga", "default", "https://maven.repository.redhat.com/ga").build()
        );
    }
}
