package org.wildfly.prospero.actions;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class InstallFolderUtilsTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testSymlinkIsConvertedToRealPath() throws Exception {
        final Path realPath = getRealPath();
        final Path link = Files.createSymbolicLink(temp.newFolder().toPath().resolve("link"), realPath);

        assertThat(InstallFolderUtils.toRealPath(link))
                .isEqualTo(realPath);
    }

    @Test
    public void testSymlinkNonExistingSubfolderIsConvertedToRealPath() throws Exception {
        final Path realPath = getRealPath();
        final Path link = Files.createSymbolicLink(temp.newFolder().toPath().resolve("link"), realPath);

        assertThat(InstallFolderUtils.toRealPath(link.resolve("test/foo")))
                .isEqualTo(realPath.resolve("test/foo"));
    }

    @Test
    public void testSymlinkSubfolderIsConvertedToRealPath() throws Exception {
        final Path realPath = getRealPath();
        Files.createDirectories(realPath.resolve("test/foo"));
        final Path link = Files.createSymbolicLink(temp.newFolder().toPath().resolve("link"), realPath);

        assertThat(InstallFolderUtils.toRealPath(link.resolve("test/foo")))
                .isEqualTo(realPath.resolve("test/foo"));
    }

    private Path getRealPath() throws IOException {
        return temp.newFolder("real").toPath().toRealPath();
    }

    @Test
    public void testNonExistingFolderIsNotConverted() throws Exception {
        final Path folder = temp.newFolder().toPath().toRealPath().resolve("link");

        assertThat(InstallFolderUtils.toRealPath(folder))
                .isEqualTo(folder);
    }

    @Test
    public void testExistingFolderIsNotConverted() throws Exception {
        final Path folder = temp.newFolder().toPath().toRealPath();

        assertThat(InstallFolderUtils.toRealPath(folder))
                .isEqualTo(folder);
    }
}