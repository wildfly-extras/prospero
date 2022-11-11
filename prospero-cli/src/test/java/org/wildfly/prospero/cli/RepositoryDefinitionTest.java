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

package org.wildfly.prospero.cli;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.wildfly.channel.Repository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.wildfly.prospero.cli.RepositoryDefinition.from;

public class RepositoryDefinitionTest {

    @Test
    public void generatesRepositoryIdsIfNotProvided() throws Exception {
        assertThat(from(List.of("http://test.te")))
                .map(Repository::getId)
                .noneMatch(id-> StringUtils.isEmpty(id));
    }

    @Test
    public void generatedRepositoryIdsAreUnique() throws Exception {
        final Set<String> collectedIds = from(List.of("http://test1.te", "http://test2.te", "http://test3.te")).stream()
                .map(Repository::getId)
                .collect(Collectors.toSet());

        assertEquals(3, collectedIds.size());
    }

    @Test
    public void keepsRepositoryIdsIfProvided() throws Exception {
        assertThat(from(List.of("repo-1::http://test1.te", "repo-2::http://test2.te", "repo-3::http://test3.te")))
                .map(Repository::getId)
                .containsExactly("repo-1", "repo-2", "repo-3");
    }

    @Test
    public void mixGeneratedAndProvidedIds() throws Exception {
        assertThat(from(List.of("repo-1::http://test1.te", "http://test2.te", "repo-3::http://test3.te")))
                .map(Repository::getId)
                .contains("repo-1", "repo-3")
                .hasSize(3)
                .noneMatch(id-> StringUtils.isEmpty(id));
    }

    @Test
    public void throwsErrorIfFormatIsIncorrect() throws Exception {
        assertThrows(ArgumentParsingException.class, ()->from(List.of("::http://test1.te")));

        assertThrows(ArgumentParsingException.class, ()->from(List.of("repo-1::")));

        assertThrows(ArgumentParsingException.class, ()->from(List.of("repo-1:::http://test1.te")));

        assertThrows(ArgumentParsingException.class, ()->from(List.of("foo::bar::http://test1.te")));

        assertThrows(ArgumentParsingException.class, ()->from(List.of("imnoturl")));
    }
}