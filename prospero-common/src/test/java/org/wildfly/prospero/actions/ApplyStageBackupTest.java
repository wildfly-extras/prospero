package org.wildfly.prospero.actions;

import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
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
    private Path candidate;
    private Path backupFolder;

    @Before
    public void setUp() throws Exception {
        server = temp.newFolder().toPath();
        candidate = temp.newFolder().toPath();
        backupFolder = server.resolve(ApplyStageBackup.BACKUP_FOLDER);
        backup = new ApplyStageBackup(server, candidate);
    }

    @After
    public void tearDown() throws Exception {
        backup.close();
    }

    @Test
    public void restoreWithEmptyList() throws Exception {
        backup.restore();
        backup.close(); // close to get rid of backup folder

        assertThat(server)
                .isEmptyDirectory();
    }

    @Test
    public void closeRemovesBackupFolder() throws Exception {
        createFile("test.txt");

        backup.recordAll();

        backup.close();

        assertThat(backupFolder)
                .doesNotExist();
    }

    @Test
    public void restoreRemovedFile() throws Exception {
        final Path testFile = createFile("test.txt");

        backup.recordAll();
        Files.delete(testFile);
        backup.restore();

        assertThat(testFile)
                .hasContent("test text");
    }

    @Test
    public void restoreChangedFile() throws Exception {
        final Path testFile = createFile("test.txt");

        backup.recordAll();
        writeFile(testFile);
        backup.restore();

        assertThat(testFile)
                .hasContent("test text");
    }

    private static void writeFile(Path testFile) throws IOException {
        if (Files.exists(testFile)) {
            Files.delete(testFile);
        }
        Files.writeString(testFile, "changed text");
    }

    @Test
    public void dontTouchUnchangedFiles() throws Exception {
        final Path testFile = createFile("test.txt");
        final FileTime lastModifiedTime = Files.getLastModifiedTime(testFile);

        backup.recordAll();
        Thread.sleep(200);
        backup.restore();

        final FileTime restoredModifiedTime = Files.getLastModifiedTime(testFile);
        assertEquals(lastModifiedTime, restoredModifiedTime);
    }

    @Test
    public void restoreFileInDirectory() throws Exception {
        final Path testFile = createFile("test/test.txt");

        backup.recordAll();
        Files.delete(testFile);
        backup.restore();

        assertThat(testFile)
                .hasContent("test text");
    }

    @Test
    public void restoreFileInDeletedDirectory() throws Exception {
        final Path testFile = createFile("test/test.txt");

        backup.recordAll();
        Files.delete(testFile);
        Files.delete(testFile.getParent());
        backup.restore();

        assertThat(testFile)
                .hasContent("test text");
    }

    @Test
    public void recordAllFilesInDirectory() throws Exception {
        final Path testFile = createFile("test/test.txt");

        backup.recordAll();
        Files.delete(testFile);
        Files.delete(testFile.getParent());
        backup.restore();

        assertThat(testFile)
                .hasContent("test text");
    }

    @Test
    public void removeAddedFile() throws Exception {
        final Path testFile = server.resolve("test.txt");

        backup.recordAll();
        writeFile(testFile);
        backup.restore();

        assertThat(testFile)
                .doesNotExist();
    }

    @Test
    public void removeAddedFileInDirectory() throws Exception {
        final Path existingFile = createFile("test/existing.txt");
        final Path testFile = server.resolve("test/test.txt");

        backup.recordAll();
        Files.createDirectories(testFile.getParent());
        writeFile(testFile);
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

        backup.recordAll();
        Files.createDirectories(testFile.getParent());
        writeFile(testFile);
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

        backup.recordAll();
        writeFile(testFile);
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

        backup.recordAll();
        Files.createDirectories(server.resolve("test/foo"));
        writeFile(testFile);
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

        backup.recordAll();
        writeFile(testFile);
        backup.restore();

        assertThat(existing)
                .hasContent("test text");
        assertThat(testFile)
                .doesNotExist();
    }

    @Test
    public void cleanBackupFolderAfterInit() throws Exception {
        final Path existingFile = backupFolder.resolve("pre-existing.file");
        Files.writeString(existingFile, "test");
        backup = new ApplyStageBackup(server, candidate);

        assertThat(existingFile)
                .doesNotExist();
    }

    @Test
    public void createNonExistingBackupFolder() throws Exception {
        backup.close();
        if (Files.exists(backupFolder)) {
            FileUtils.forceDelete(backupFolder.toFile());
        }
        backup = new ApplyStageBackup(server, candidate);

        assertThat(backupFolder)
                .exists();
    }

    @Test
    public void throwsExceptionIfBackupFolderIsFile() throws Exception {
        backup.close();
        if (Files.exists(backupFolder)) {
            FileUtils.forceDelete(backupFolder.toFile());
        }
        Files.writeString(backupFolder, "foo");
         Assertions.assertThatThrownBy(()->new ApplyStageBackup(server, candidate))
                 .isInstanceOf(RuntimeException.class)
                 .hasMessageContaining("Unable to create backup");

    }

    @Test
    public void ignoreNonReadableUserFiles() throws Exception {
        final Path testFile = server.resolve("test.txt");
        final Path testDir = server.resolve("test");
        Files.createDirectories(testDir);
        writeFile(testFile);

        try {
            Assume.assumeTrue("Skipping test because OS doesn't support setting folders un-readable", testFile.toFile().setReadable(false));
            Assume.assumeTrue("Skipping test because OS doesn't support setting folders un-readable", testDir.toFile().setReadable(false));

            backup.recordAll();
            backup.restore();

            assertThat(testFile)
                    .exists();
            assertThat(testDir)
                    .exists();
        } finally {
            testFile.toFile().setReadable(true);
            testDir.toFile().setReadable(true);
        }
    }

    @Test
    public void throwExceptionOnNonReadableServerFiles() throws Exception {
        final Path testFile = server.resolve("test");
        final Path candidateFile = candidate.resolve("test");
        Files.createDirectories(testFile);
        Files.createDirectories(candidateFile);

        try {
            Assume.assumeTrue("Skipping test because OS doesn't support setting folders un-readable", testFile.toFile().setReadable(false));

        Assertions.assertThatThrownBy(()->backup.recordAll())
                        .isInstanceOf(AccessDeniedException.class)
                        .hasMessageContaining(testFile.toString());

        Assertions.assertThatThrownBy(()->backup.restore())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining(testFile.toString());

        assertThat(testFile)
                .exists();
        } finally {
            testFile.toFile().setReadable(true);
        }
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