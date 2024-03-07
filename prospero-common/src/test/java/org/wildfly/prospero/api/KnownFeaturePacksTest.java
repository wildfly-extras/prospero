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

package org.wildfly.prospero.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.jboss.galleon.ProvisioningException;
import org.junit.Test;
import org.wildfly.channel.MavenCoordinate;
import org.wildfly.channel.Repository;
import org.wildfly.prospero.galleon.GalleonUtils;
import org.wildfly.prospero.model.KnownFeaturePack;

import static org.assertj.core.api.Assertions.assertThat;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;

public class KnownFeaturePacksTest {

    @Test public void testFpFromGalleonConfig() throws ProvisioningException, XMLStreamException, URISyntaxException {
        KnownFeaturePack knownFeaturePack = KnownFeaturePacks.getByName("known-fpl");

        assertThat(knownFeaturePack).isNotNull();
        assertThat(knownFeaturePack.getGalleonConfiguration()).isEqualTo(
                new URI("classpath:galleon-provisioning.xml"));
        assertThat(knownFeaturePack.getChannels().get(0)).satisfies(channel -> {
            assertThat(channel.getManifestCoordinate().getMaven()).isEqualTo(new MavenCoordinate("test", "one", null));
            assertThat(channel.getRepositories()).containsOnly(new Repository("central", "https://repo1.maven.org/maven2/"));
        });

        GalleonProvisioningConfig galleonConfig = GalleonUtils.loadProvisioningConfig(knownFeaturePack.getGalleonConfiguration());
        assertThat(galleonConfig.getOptions()).containsOnly(Map.entry("jboss-bulk-resolve-artifacts", "true"));
        assertThat(galleonConfig.getFeaturePackDeps().iterator().next()).satisfies(fp -> {
            assertThat(fp.getLocation().toString()).isEqualTo("org.wildfly.core:wildfly-core-galleon-pack:zip");
            assertThat(fp.getExcludedPackages()).containsOnly("package2");
            assertThat(fp.getIncludedPackages()).containsOnly("package1");
        });

    }

}
