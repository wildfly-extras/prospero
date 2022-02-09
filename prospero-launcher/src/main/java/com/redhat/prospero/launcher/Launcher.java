/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.prospero.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
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

public class Launcher {

   public static void main(String[] args) throws Exception {
      final Path userHome = Paths.get(System.getProperty("user.home"));
      final Path installerLib = userHome.resolve(".jboss-installer").resolve("lib");

      List<URL> jars = new ArrayList<>();

      if (Files.exists(installerLib)) {
         // do clean-up
         removeOldVersions(installerLib);

         // update installer
         System.out.println("Checking for installer updates");
         for (File file : installerLib.toFile().listFiles((d) -> d.getName().endsWith(".jar"))) {
            jars.add(file.toURI().toURL());
         }
         update(installerLib, jars);

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

         update(tempLib, jars);
         System.out.println("Starting installer");
         startInstaller(args, installerLib);
      }
   }

   private static List<URL> unzipInitialDeps(Path tempLib) throws URISyntaxException, IOException {
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
   }

   private static void update(Path installerLib,
                                 List<URL> jars) throws ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException, IOException {
      URLClassLoader tempCl = new URLClassLoader(jars.toArray(new URL[]{}));
      Thread.currentThread().setContextClassLoader(tempCl);
      Class c = Class.forName("com.redhat.prospero.bootstrap.BootstrapUpdater", true, tempCl);
      final Object o = c.newInstance();
      final Method handleArgs = c.getMethod("update");
      final List<Path> res = (List<Path>) handleArgs.invoke(o);

      tempCl.close();

      try(PrintWriter fw = new PrintWriter(installerLib.resolve(".obsolete-lib.txt").toFile())) {
         for (Path lib : res) {
            try {
               Files.delete(lib);
            } catch (IOException e) {
               fw.println(lib.getFileName().toString());
            }
         }
      }
   }

   private static void removeOldVersions(Path installerLib) throws IOException {
      if (Files.exists(installerLib.resolve(".obsolete-lib.txt"))) {
         try (BufferedReader br = new BufferedReader(new FileReader(installerLib.resolve(".obsolete-lib.txt").toFile()))) {
            br.lines().forEach((fileName)-> {
               try {
                  Files.deleteIfExists(installerLib.resolve(fileName));
               } catch (IOException e) {
                  System.out.println("Unable to delete outdated lib at " + installerLib.resolve(fileName).toAbsolutePath());
               }
            });
         }
         Files.delete(installerLib.resolve(".obsolete-lib.txt"));
      }
   }

   private static void startInstaller(String[] args,
                                 Path installerLib) throws MalformedURLException, ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
      ArrayList<URL> jars = new ArrayList<>();
      for (File file : installerLib.toFile().listFiles((d) -> d.getName().endsWith(".jar"))) {
         jars.add(file.toURI().toURL());
      }
      final URLClassLoader cl = new URLClassLoader(jars.toArray(new URL[]{}));
      Thread.currentThread().setContextClassLoader(cl);

      Class c = Class.forName("com.redhat.prospero.cli.CliMain", true, cl);
      final Object o = c.newInstance();
      final Method handleArgs = c.getMethod("handleArgs", String[].class);
      handleArgs.invoke(o, new Object[]{args});
   }
}
