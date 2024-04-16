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

package org.wildfly.prospero.model;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.*;

public class FeaturePackTemplateManagerTest {
    @Test
    public void emptyConfigFileReturnsNull() throws Exception {
        final FeaturePackTemplateManager featurePackTemplateManager = new FeaturePackTemplateManager(
                new FeaturePackTemplateList(Collections.emptyList())
        );
        assertNull(featurePackTemplateManager.find("org.test", "addOnOne", "1.0.0"));
    }

    @Test
    public void nonExistingConfigReturnsNull() throws Exception {
        final FeaturePackTemplateManager featurePackTemplateManager = new FeaturePackTemplateManager(
                new FeaturePackTemplateList(List.of(
                        new FeaturePackTemplate.Builder("org.test", "addOnOne", "[0,)").build()
                ))
        );
        assertNull(featurePackTemplateManager.find("i.dont", "exist", "1.0.0"));
    }

    @Test
    public void matchedConfigWithSetVersionReturnsProvisioningConfig() throws Exception {
        final FeaturePackTemplateManager featurePackTemplateManager = new FeaturePackTemplateManager(
                new FeaturePackTemplateList(List.of(
                        new FeaturePackTemplate.Builder("org.test", "addOnOne", "1.0.0").build()
                ))
        );

        final FeaturePackTemplate recipe = featurePackTemplateManager.find("org.test", "addOnOne", "1.0.0");
        assertNotNull(recipe);
        assertThat(recipe)
                .hasFieldOrPropertyWithValue("groupId", "org.test")
                .hasFieldOrPropertyWithValue("artifactId", "addOnOne")
                .hasFieldOrPropertyWithValue("version", "1.0.0");
    }

    @Test
    public void matchedConfigWithVersionRangeReturnsProvisioningConfig() throws Exception {
        final FeaturePackTemplateManager featurePackTemplateManager = new FeaturePackTemplateManager(
                new FeaturePackTemplateList(List.of(
                        new FeaturePackTemplate.Builder("org.test", "addOnOne", "[0,)").build()
                ))
        );

        final FeaturePackTemplate recipe = featurePackTemplateManager.find("org.test", "addOnOne", "1.0.0");
        assertNotNull(recipe);
        assertThat(recipe)
                .hasFieldOrPropertyWithValue("groupId", "org.test")
                .hasFieldOrPropertyWithValue("artifactId", "addOnOne")
                .hasFieldOrPropertyWithValue("version", "[0,)");
    }

    @Test
    public void versionOutsideRangeThrowsException() throws Exception {
        final FeaturePackTemplateManager featurePackTemplateManager = new FeaturePackTemplateManager(
                new FeaturePackTemplateList(List.of(
                        new FeaturePackTemplate.Builder("org.test", "addOnOne", "[0,1.1)").build()
                ))
        );

        assertThatThrownBy(()-> featurePackTemplateManager.find("org.test", "addOnOne","1.2.0"))
                .isInstanceOf(FeaturePackTemplateManager.FeatureTemplateVersionMismatchException.class);
    }

    @Test
    public void correctVersionRangeIsPickedIfMultipleRecords() throws Exception {
        final FeaturePackTemplateManager featurePackTemplateManager = new FeaturePackTemplateManager(
                new FeaturePackTemplateList(List.of(
                        new FeaturePackTemplate.Builder("org.test", "addOnOne", "[0,1.1)").build(),
                        new FeaturePackTemplate.Builder("org.test", "addOnOne", "[1.1,)").build()
                ))
        );

        final FeaturePackTemplate recipe = featurePackTemplateManager.find("org.test", "addOnOne", "1.2.0");
        assertThat(recipe)
                .hasFieldOrPropertyWithValue("groupId", "org.test")
                .hasFieldOrPropertyWithValue("artifactId", "addOnOne")
                .hasFieldOrPropertyWithValue("version", "[1.1,)");
    }
}