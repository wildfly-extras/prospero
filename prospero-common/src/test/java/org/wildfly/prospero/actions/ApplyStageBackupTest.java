package org.wildfly.prospero.actions;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class ApplyStageBackupTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private ApplyStageBackup backup;
    private Path server;
    private Path backupPath;

    @Before
    public void setUp() throws Exception {
        server = temp.newFolder().toPath();
        backupPath = temp.newFolder("backup").toPath();
        backup = new ApplyStageBackup(server, backupPath);
    }

    @After
    public void tearDown() throws Exception {
        backup.close();
    }

    @Test
    public void restoreWithEmptyList() throws Exception {
        backup.restore();

        assertThat(server)
                .isEmptyDirectory();
    }

    @Test
    public void closeRemovesBackupFolder() throws Exception {
        final Path testFile = createFile("test.txt");

        backup.record(testFile);

        backup.close();

        assertThat(backupPath)
                .doesNotExist();
    }

    @Test
    public void restoreRemovedFile() throws Exception {
        final Path testFile = createFile("test.txt");

        backup.record(testFile);
        Files.delete(testFile);
        backup.restore();

        assertThat(testFile)
                .hasContent("test text");
    }

    @Test
    public void restoreChangedFile() throws Exception {
        final Path testFile = createFile("test.txt");

        backup.record(testFile);
        Files.writeString(testFile, "changed text");
        backup.restore();

        assertThat(testFile)
                .hasContent("test text");
    }

    @Test
    public void dontTouchUnchangedFiles() throws Exception {
        final Path testFile = createFile("test.txt");
        final FileTime lastModifiedTime = Files.getLastModifiedTime(testFile);

        backup.record(testFile);
        Thread.sleep(200);
        backup.restore();

        final FileTime restoredModifiedTime = Files.getLastModifiedTime(testFile);
        assertEquals(lastModifiedTime, restoredModifiedTime);
    }

    @Test
    public void restoreFileInDirectory() throws Exception {
        final Path testFile = createFile("test/test.txt");

        backup.record(testFile);
        Files.delete(testFile);
        backup.restore();

        assertThat(testFile)
                .hasContent("test text");
    }

    @Test
    public void restoreFileInDeletedDirectory() throws Exception {
        final Path testFile = createFile("test/test.txt");

        backup.record(testFile);
        Files.delete(testFile);
        Files.delete(testFile.getParent());
        backup.restore();

        assertThat(testFile)
                .hasContent("test text");
    }

    @Test
    public void recordAllFilesInDirectory() throws Exception {
        final Path testFile = createFile("test/test.txt");

        backup.record(testFile.getParent());
        Files.delete(testFile);
        Files.delete(testFile.getParent());
        backup.restore();

        assertThat(testFile)
                .hasContent("test text");
    }

    @Test
    public void recordChangedFileTwice() throws Exception {
        final Path testFile = createFile("test.txt");

        backup.record(testFile);
        backup.record(testFile);
        Files.writeString(testFile, "changed text");
        backup.restore();

        assertThat(testFile)
                .hasContent("test text");
    }

    @Test
    public void removeAddedFile() throws Exception {
        final Path testFile = server.resolve("test.txt");

        backup.record(testFile);
        Files.writeString(testFile, "changed text");
        backup.restore();

        assertThat(testFile)
                .doesNotExist();
    }

    @Test
    public void removeAddedFileInDirectory() throws Exception {
        final Path existingFile = createFile("test/existing.txt");
        final Path testFile = server.resolve("test/test.txt");

        backup.record(testFile);
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "changed text");
        backup.restore();

        assertThat(testFile)
                .doesNotExist();
        assertThat(testFile.getParent())
                .exists();
        assertThat(existingFile)
                .exists();
    }

    @Test
    public void preserveExistingFilesInDirectoryWithAddedFile() throws Exception {
        final Path testFile = server.resolve("test/test.txt");

        backup.record(testFile);
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "changed text");
        backup.restore();

        assertThat(testFile)
                .doesNotExist();
        assertThat(testFile.getParent())
                .doesNotExist();
    }

    @Test
    public void removeAddedFileInRecordedDirectory() throws Exception {
        final Path testFile = server.resolve("test/test.txt");
        Files.createDirectories(testFile.getParent());

        backup.record(testFile.getParent());
        Files.writeString(testFile, "changed text");
        backup.restore();

        assertThat(testFile)
                .doesNotExist();
        assertThat(testFile.getParent())
                .exists();
    }

    @Test
    public void removeAddedFolderInRecordedDirectory() throws Exception {
        final Path testFile = server.resolve("test/foo/test.txt");
        Files.createDirectories(server.resolve("test"));

        backup.record(server.resolve("test"));
        Files.createDirectories(server.resolve("test/foo"));
        Files.writeString(testFile, "changed text");
        backup.restore();

        assertThat(testFile)
                .doesNotExist();
        assertThat(testFile.getParent())
                .doesNotExist();
        assertThat(testFile.getParent().getParent())
                .exists();
    }

    @Test
    public void dontRemoveUnchangedFiles() throws Exception {
        final Path testFile = server.resolve("test/test.txt");
        final Path existing = createFile("test/existing.txt");

        backup.record(testFile);
        Files.writeString(testFile, "changed text");
        backup.restore();

        assertThat(existing)
                .hasContent("test text");
        assertThat(testFile)
                .doesNotExist();
    }

    private Path createFile(String path) throws IOException {
        final Path testFile = server.resolve(path);
        if (!Files.exists(testFile.getParent())) {
            Files.createDirectories(testFile.getParent());
        }
        Files.writeString(testFile, "test text");
        return testFile;
    }

}