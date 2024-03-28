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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FeaturePackTemplateListTest {

    @Test
    public void deserializeAllFieldsTest() throws Exception {
        FeaturePackTemplate t1 = new FeaturePackTemplate.Builder("group1", "artifact1", "1.0.0")
                .setRequiresLayers(true)
                .setSupportsCustomization(false)
                .setReplacesDependency("replaceme")
                .addTransitiveDependency("transatice1")
                .addAdditionalPackage("additional1")
                .build();
        final String yaml = new FeaturePackTemplateList(List.of(t1)).toYaml();

        assertThat(FeaturePackTemplateList.read(yaml).getRecipes())
                .containsExactly(t1);
    }

    @Test
    public void readDefaultValues() throws Exception {
        final String yaml = "---\n" +
                "schemaVersion: 1.0.0\n" +
                "recipes:\n" +
                "  - groupId: group1\n" +
                "    artifactId: artifact1\n" +
                "    version: 1.0.0";

        final FeaturePackTemplate template = FeaturePackTemplateList.read(yaml).getRecipes().get(0);

        assertFalse(template.isRequiresLayers());
        assertTrue(template.isSupportsCustomization());
        assertNull(template.getReplacesDependency());
        assertNull(template.getTransitiveDependency());
        assertThat(template.getAdditionalPackages()).isEmpty();
    }

    @Test
    public void writeEmptyList() throws Exception {
        assertThat(new FeaturePackTemplateList(Collections.emptyList()).toYaml().trim())
                .isEqualTo("---\nschemaVersion: \"1.0.0\"");
    }

    @Test
    public void writeListWithOneRecipe() throws Exception {
        assertIsSerialized(List.of(
                new FeaturePackTemplate.Builder("org.test", "addonOne", "[1,0)").build()
        ));
    }

    @Test
    public void writeListWithTwoRecipes() throws Exception {
        assertIsSerialized(List.of(
                new FeaturePackTemplate.Builder("org.test", "addonOne", "[1,0)").build(),
                new FeaturePackTemplate.Builder("org.test", "addonTwo", "[1,0)").build()
        ));
    }

    @Test
    public void requireSchemaToReadTheList() throws Exception {
        assertThatThrownBy(()-> FeaturePackTemplateList.read("---"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown schema version for the recipe book");
    }

    @Test
    public void unknownSchemaVersionThrowsException() throws Exception {
        assertThatThrownBy(()-> FeaturePackTemplateList.read("---\nschemaVersion:\"i.dont.exist\""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown schema version for the recipe book");
    }

    @Test
    public void onlyOneEntryPerGavIsPermitted() throws Exception {
        final List<FeaturePackTemplate> mappings = List.of(
                new FeaturePackTemplate.Builder("org.test", "addonOne", "[1,0)").build(),
                new FeaturePackTemplate.Builder("org.test", "addonOne", "[1,0)").build()
        );
        assertThatThrownBy(()->new FeaturePackTemplateList(mappings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicated mappings for GAV: org.test:addonOne:[1,0)");
    }

    private static void assertIsSerialized(List<FeaturePackTemplate> mappings) throws JsonProcessingException {
        final FeaturePackTemplateList origin = new FeaturePackTemplateList(mappings);
        final String yaml = origin.toYaml();
        final FeaturePackTemplateList read = FeaturePackTemplateList.read(yaml);

        assertThat(read)
                .isEqualTo(origin);
    }
}