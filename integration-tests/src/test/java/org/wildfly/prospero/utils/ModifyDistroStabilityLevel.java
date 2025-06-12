package org.wildfly.prospero.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.jar.Manifest;

/**
 * Modifies the stability level of a prospero-commons jar. Used to set up distro for testing
 */
public class ModifyDistroStabilityLevel {
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("Unexpected count of arguments. Usage:");
            System.out.printf("%s <PROSPERO_DIST_DIR> <VERSION> <STABILITY_LEVEL>%n", ModifyDistroStabilityLevel.class.getCanonicalName());
        }
        final String baseDir = args[0];
        final String version = args[1];
        final String stabilityLevel = args[2];

        new ModifyDistroStabilityLevel().applyStabilityLevel(baseDir, version, stabilityLevel);
    }

    public void applyStabilityLevel(String baseDir, String version, String stabilityLevel) throws IOException {
        System.out.printf("Changing prospero distribution in %s version %s to stability level %s%n", baseDir, version, stabilityLevel);

        final Path modulesDir = Path.of(baseDir).resolve("modules");

        Files.walkFileTree(modulesDir, new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().equals("prospero-common-%s.jar".formatted(version))) {
                    System.out.printf("Found prospero archive at %s", file);
                    modifyStabilityLevel(file, stabilityLevel);
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void modifyStabilityLevel(Path archive, String stabilityLevel) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(archive)) {
            final Path source = fs.getPath("/META-INF/MANIFEST.MF");
            final Manifest manifest;
            try (InputStream is = Files.newInputStream(source)) {
                manifest = new Manifest(is);
                manifest.getMainAttributes().putValue("JBoss-Product-Stability", stabilityLevel);
                manifest.getMainAttributes().putValue("JBoss-Product-Minimal-Stability", stabilityLevel);
            }

            try (OutputStream out = Files.newOutputStream(source)) {
                manifest.write(out);
            }
        }
    }
}
