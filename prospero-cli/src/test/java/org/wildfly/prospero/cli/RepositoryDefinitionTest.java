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
import org.junit.Before;
import org.junit.Test;
import org.wildfly.channel.Repository;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.wildfly.prospero.cli.RepositoryDefinition.from;

public class RepositoryDefinitionTest {

    private String tempRepoURL;

    @Before
    public void setUp() throws Exception {
        tempRepoURL = Path.of(".").normalize().toAbsolutePath().toUri().toString().replace("///","/");
    }

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

    @Test
    public void throwsErrorIfFormatIsIncorrectForFileURLorPathDoesNotExist() throws Exception {
        assertThrows(ArgumentParsingException.class, ()->from(List.of("::"+tempRepoURL)));

        assertThrows(ArgumentParsingException.class, ()->from(List.of("repo-1::")));

        assertThrows(ArgumentParsingException.class, ()->from(List.of("repo-1:::"+tempRepoURL)));

        assertThrows(ArgumentParsingException.class, ()->from(List.of("foo::bar::"+tempRepoURL)));
    }

    @Test
    public void testCorrectRelativeOrAbsolutePathForFileURL() throws Exception {
        Repository repository = new Repository("temp-repo-0", tempRepoURL);
        List<Repository> actualList = from(List.of("file:../prospero-cli"));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertTrue(actualList.contains(repository));

        actualList = from(List.of(("file:/"+(System.getProperty("user.dir").replaceAll("\\\\","/"))).replace("//","/")));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertTrue(actualList.contains(repository));

        actualList = from(List.of(("file:///"+(System.getProperty("user.dir").replaceAll("\\\\","/"))).replace("////","///")));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertTrue(actualList.contains(repository));

        actualList = from(List.of(System.getProperty("user.dir")));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertTrue(actualList.contains(repository));

        actualList = from(List.of(".."+File.separator+"prospero-cli"));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertTrue(actualList.contains(repository));
    }
}