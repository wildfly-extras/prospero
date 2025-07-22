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
import org.apache.commons.lang3.SystemUtils;
import org.assertj.core.api.Assertions;
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

    private String tempRepoUrlNoHostForm;
    private String tempRepoUrlEmptyHostForm;

    @Before
    public void setUp() throws Exception {
        tempRepoUrlEmptyHostForm = Path.of(".").normalize().toAbsolutePath().toUri().toString();
        tempRepoUrlNoHostForm = tempRepoUrlEmptyHostForm.replace("///","/");
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
    public void throwsErrorIfFormatIsIncorrect() {
        assertThrows(ArgumentParsingException.class, ()->from(List.of("::http://test1.te")));

        assertThrows(ArgumentParsingException.class, ()->from(List.of("repo-1::")));

        assertThrows(ArgumentParsingException.class, ()->from(List.of("repo-1:::http://test1.te")));

        assertThrows(ArgumentParsingException.class, ()->from(List.of("foo::bar::http://test1.te")));
    }

    @Test
    public void throwsErrorIfFormatIsIncorrectForFileURLorPathDoesNotExist() throws Exception {
        assertThrows(ArgumentParsingException.class, ()->from(List.of("::"+ tempRepoUrlNoHostForm)));

        assertThrows(ArgumentParsingException.class, ()->from(List.of("repo-1::")));

        assertThrows(ArgumentParsingException.class, ()->from(List.of("repo-1:::"+ tempRepoUrlNoHostForm)));

        assertThrows(ArgumentParsingException.class, ()->from(List.of("foo::bar::"+ tempRepoUrlNoHostForm)));
    }

    @Test
    public void testCorrectRelativeOrAbsolutePathForFileURL() throws Exception {
        Repository repository = new Repository("temp-repo-0", tempRepoUrlNoHostForm);
        List<Repository> actualList = from(List.of("file:../prospero-cli"));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertThat(actualList)
                .contains(repository);

        actualList = from(List.of("temp-repo-0::file:../prospero-cli"));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertThat(actualList)
                .contains(repository);

        actualList = from(List.of(("file:/"+(System.getProperty("user.dir").replaceAll("\\\\","/"))).replace("//","/")));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertTrue(actualList.contains(repository));

        actualList = from(List.of(("temp-repo-0::file:/"+(System.getProperty("user.dir").replaceAll("\\\\","/"))).replace("//","/")));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertTrue(actualList.contains(repository));

        actualList = from(List.of(("file:///"+(System.getProperty("user.dir").replaceAll("\\\\","/"))).replace("////","///")));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertTrue(actualList.contains(repository));

        actualList = from(List.of(("temp-repo-0::file:///"+(System.getProperty("user.dir").replaceAll("\\\\","/"))).replace("////","///")));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertTrue(actualList.contains(repository));

        actualList = from(List.of(System.getProperty("user.dir")));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertTrue(actualList.contains(repository));

        actualList = from(List.of("temp-repo-0::" + System.getProperty("user.dir")));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertTrue(actualList.contains(repository));

        actualList = from(List.of(".."+File.separator+"prospero-cli"));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertTrue(actualList.contains(repository));

        actualList = from(List.of("temp-repo-0::.."+File.separator+"prospero-cli"));

        assertNotNull(actualList);
        assertEquals(1, actualList.size());
        assertTrue(actualList.contains(repository));
    }

    @Test
    public void testNonExistingFile() throws Exception {
        Assertions.assertThatThrownBy(() -> from(List.of("idontexist")))
                .hasMessageContaining(String.format(
                        "The provided path [%s] doesn't exist or is not accessible. The local repository has to be an existing, readable folder.",
                        Path.of("idontexist").toAbsolutePath()));
    }

    @Test
    public void testNonExistingFileUri() throws Exception {
        Assertions.assertThatThrownBy(() -> from(List.of("file:idontexist")))
                .hasMessageContaining(String.format(
                        "The provided path [%s] doesn't exist or is not accessible. The local repository has to be an existing, readable folder.",
                        Path.of("idontexist").toAbsolutePath()));
    }

    @Test
    public void testNormalization() throws Exception {
        String cwdPath = Path.of(System.getProperty("user.dir")).toUri().getPath();

        assertThat(RepositoryDefinition.parseRepositoryLocation("file://" + cwdPath, true)) // file:///home/...
                .isEqualTo("file:" + cwdPath);

        assertThat(RepositoryDefinition.parseRepositoryLocation("file:" + cwdPath, true)) // file:/home/...
                .isEqualTo("file:" + cwdPath);

        assertThat(RepositoryDefinition.parseRepositoryLocation("file://host/some/path", true))
                .isEqualTo("file://host/some/path");

        assertThat(RepositoryDefinition.parseRepositoryLocation("file:../prospero-cli", true))
                .isEqualTo("file:" + cwdPath);

        try {
            RepositoryDefinition.parseRepositoryLocation("file://../prospero-cli", true); // This is interpreted as local absolute path "/../path".
            fail("This path should fail because it doesn't exist.");
        } catch (ArgumentParsingException e) {
            // pass
        }

        // On Linux following is interpreted as relative path, on Windows it's an absolute path
        if (SystemUtils.IS_OS_WINDOWS) {
            assertThat(RepositoryDefinition.parseRepositoryLocation("c:foo/bar", false)) // interpreted as local relative path
                    .isEqualTo("file:/C:/foo/bar");
        } else {
            assertThat(RepositoryDefinition.parseRepositoryLocation("a:foo/bar", false)) // interpreted as local relative path
                    .isEqualTo("file:" + cwdPath + "a:foo/bar");
        }
    }

    @Test
    public void testNormalizationWindowsPaths() throws Exception {
        String cwdPath = Path.of(System.getProperty("user.dir")).toUri().getPath();

        assertThat(RepositoryDefinition.parseRepositoryLocation("file:/c:/some/path", false))
                .isEqualTo("file:/c:/some/path");

        assertThat(RepositoryDefinition.parseRepositoryLocation("file:///c:/some/path", false))
                .isEqualTo("file:/c:/some/path");

        assertThat(RepositoryDefinition.parseRepositoryLocation("file://host/c:/some/path", false))
                .isEqualTo("file://host/c:/some/path");

        // On Linux following is interpreted as relative path, on Windows it's an absolute path
        if (SystemUtils.IS_OS_WINDOWS) {
            assertThat(RepositoryDefinition.parseRepositoryLocation("c:/foo/bar", false))
                    .isEqualTo("file:/c:/foo/bar");
        } else {
            assertThat(RepositoryDefinition.parseRepositoryLocation("c:/foo/bar", false))
                    .isEqualTo("file:" + cwdPath + "c:/foo/bar");
        }
    }

}