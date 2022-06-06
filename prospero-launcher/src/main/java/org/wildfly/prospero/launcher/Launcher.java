/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Launcher {

    private static final Logger logger = LoggerFactory.getLogger(Launcher.class);

    public static void main(String[] args) {
        try {
            executeCommand(args);
        } catch (LauncherException | IOException e) {
            System.err.println("Failed to launch installer: " + e.getMessage());
            logger.error("Failed to launch installer", e);
        }
    }

    private static void executeCommand(String[] args) throws LauncherException, IOException {
        final Path userHome = Paths.get(System.getProperty("user.home"));
        final Path installerLib = userHome.resolve(".jboss-installer").resolve("lib");

        List<URL> jars = new ArrayList<>();

        if (Files.exists(installerLib)) {
            // do clean-up
            try {
                removeOldVersions(installerLib);
            } catch (IOException e) {
                throw new LauncherException("Unable to remove old versions of installer dependencies", e);
            }

            // update installer
            System.out.println("Checking for installer updates");
            for (File file : installerLib.toFile().listFiles((d) -> d.getName().endsWith(".jar"))) {
                jars.add(file.toURI().toURL());
            }
            update(installerLib, jars, args);

            // perform installation
            System.out.println("Starting installer");
            startInstaller(args, installerLib);
        } else {
            // copy all the jars from lib into temp folder
            System.out.println("Checking for installer updates");
            installerLib.toFile().mkdirs();
            final Path tempLib = Files.createTempDirectory("jboss-installer-lib");
            tempLib.toFile().deleteOnExit();

            jars = unzipInitialDeps(tempLib);

            update(tempLib, jars, args);
            System.out.println("Starting installer");
            startInstaller(args, installerLib);
        }
    }

    private static List<URL> unzipInitialDeps(Path tempLib) throws LauncherException {
        try {
            List<URL> jars = new ArrayList<>();
            URI resource = Launcher.class.getResource("").toURI();
            final FileSystem fileSystem = FileSystems.newFileSystem(resource, Collections.emptyMap());
            final Path jarLibs = fileSystem.getPath("lib");
            Files.walkFileTree(jarLibs, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    final Path targetPath = tempLib.resolve(file.getFileName().toString());
                    Files.copy(file, targetPath);
                    targetPath.toFile().deleteOnExit();
                    jars.add(targetPath.toUri().toURL());
                    return FileVisitResult.CONTINUE;
                }
            });
            return jars;
        } catch (URISyntaxException | IOException e) {
            throw new LauncherException("Unable to extract installer dependencies", e);
        }
    }

    private static void update(Path installerLib, List<URL> jars, String[] args) throws LauncherException {
        URLClassLoader tempCl = new URLClassLoader(jars.toArray(new URL[]{}));
        Thread.currentThread().setContextClassLoader(tempCl);
        final List<Path> res;
        try {
            Class c = Class.forName("org.wildfly.prospero.bootstrap.BootstrapUpdater", true, tempCl);
            final Object o = c.newInstance();
            final Method handleArgs = c.getMethod("update", String[].class);
            res = (List<Path>) handleArgs.invoke(o, new Object[]{args});

            tempCl.close();
        } catch (ClassNotFoundException | InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException | IOException e) {
            throw new LauncherException("Failed to update installer dependencies", e);
        }

        try (PrintWriter fw = new PrintWriter(installerLib.resolve(".obsolete-lib.txt").toFile())) {
            for (Path lib : res) {
                try {
                    Files.delete(lib);
                } catch (IOException e) {
                    fw.println(lib.getFileName().toString());
                }
            }
        } catch (FileNotFoundException e) {
            throw new LauncherException("Failed to save list of updated dependencies", e);
        }
    }

    private static void removeOldVersions(Path installerLib) throws IOException {
        if (Files.exists(installerLib.resolve(".obsolete-lib.txt"))) {
            try (BufferedReader br = new BufferedReader(new FileReader(installerLib.resolve(".obsolete-lib.txt").toFile()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    Files.deleteIfExists(installerLib.resolve(line));
                }
            }
            Files.delete(installerLib.resolve(".obsolete-lib.txt"));
        }
    }

    private static void startInstaller(String[] args,
                                       Path installerLib) throws LauncherException {
        try {
            ArrayList<URL> jars = new ArrayList<>();
            for (File file : installerLib.toFile().listFiles((d) -> d.getName().endsWith(".jar"))) {
                jars.add(file.toURI().toURL());
            }
            final URLClassLoader cl = new URLClassLoader(jars.toArray(new URL[]{}));
            Thread.currentThread().setContextClassLoader(cl);

            Class c = Class.forName("org.wildfly.prospero.cli.CliMain", true, cl);
            final Object o = c.newInstance();
            final Method handleArgs = c.getMethod("handleArgs", String[].class);
            handleArgs.invoke(o, new Object[]{args});
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | IOException e) {
            throw new LauncherException("Failed to start installer", e);
        } catch (InvocationTargetException e) {
            throw new LauncherException("Error when performing installation", e.getCause());
        }
    }
}
