package org.wildfly.prospero.it.utils;

import org.assertj.core.internal.BinaryDiff;
import org.assertj.core.internal.Diff;
import org.assertj.core.util.diff.Delta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;

public class DirectoryComparator {
    private static final BinaryDiff BINARY_DIFF = new BinaryDiff();

    private static class FileChange {
        final Path expected;
        final Path actual;

        public FileChange(Path expected, Path actual) {
            this.expected = expected;
            this.actual = actual;
        }
    }

    public static void assertNoChanges(Path originalServer, Path targetDir) throws IOException {
        final List<FileChange> changes = new ArrayList<>();

        // get a list of files present only in the expected server or ones present in both but with different content
        Files.walkFileTree(originalServer, listAddedAndModifiedFiles(originalServer, targetDir, changes));

        // get a list of files present only in the actual server
        Files.walkFileTree(targetDir, listRemovedFiles(originalServer, targetDir, changes));

        if (!changes.isEmpty()) {
            fail(describeChanges(originalServer, targetDir, changes));
        }
    }

    private static String describeChanges(Path originalServer, Path targetDir, List<FileChange> changes) throws IOException {
        final StringBuilder sb = new StringBuilder("Expected folders to be the same, but:\n");
        final Diff textDiff = new Diff();

        for (FileChange change : changes) {
            if (change.actual == null) {
                sb.append(" [R] ").append(originalServer.relativize(change.expected)).append("\n");
            } else if (change.expected == null) {
                sb.append(" [A] ").append(targetDir.relativize(change.actual)).append("\n");
            } else {
                sb.append(" [M] ").append(targetDir.relativize(change.actual)).append("\n");
                final String fileName = change.actual.toString();
                if (fileName.endsWith("xml") || fileName.endsWith("txt") || fileName.endsWith("yaml")) {
                    final List<Delta<String>> diff = textDiff.diff(change.actual, StandardCharsets.UTF_8, change.expected, StandardCharsets.UTF_8);
                    diff.forEach(d->{
                        sb.append("    Line ").append(d.lineNumber()).append(":").append("\n");
                        sb.append("      - ").append(String.join("\n      - ", d.getOriginal().getLines())).append("\n");
                        sb.append("      + ").append(String.join("\n      + ", d.getRevised().getLines())).append("\n");
                    });
                }
            }
        }
        return sb.toString();
    }

    private static SimpleFileVisitor<Path> listRemovedFiles(Path originalServer, Path targetDir, List<FileChange> changes) {
        return new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                final Path relative = targetDir.relativize(file);
                final Path expectedFile = originalServer.resolve(relative);
                if (!Files.exists(expectedFile)) {
                    changes.add(new FileChange(null, file));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                final Path relative = targetDir.relativize(dir);
                final Path expectedFile = originalServer.resolve(relative);
                if (!Files.exists(expectedFile)) {
                    changes.add(new FileChange(null, dir));
                }
                return FileVisitResult.CONTINUE;
            }
        };
    }

    private static SimpleFileVisitor<Path> listAddedAndModifiedFiles(Path originalServer, Path targetDir, List<FileChange> changes) {
        return new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                final Path relative = originalServer.relativize(file);
                final Path actualFile = targetDir.resolve(relative);
                if (!Files.exists(actualFile)) {
                    changes.add(new FileChange(file, null));
                } else if (BINARY_DIFF.diff(actualFile, Files.readAllBytes(file)).hasDiff()) {
                    changes.add(new FileChange(file, actualFile));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                final Path relative = originalServer.relativize(dir);
                final Path actualFile = targetDir.resolve(relative);
                if (!Files.exists(actualFile)) {
                    changes.add(new FileChange(dir, null));
                }
                return FileVisitResult.CONTINUE;
            }
        };
    }
}
