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

package com.redhat.prospero.cli.actions;

import com.redhat.prospero.ChannelMavenArtifactRepositoryManager;
import com.redhat.prospero.cli.MavenFallback;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.universe.FeaturePackLocation;

public class GalleonProvision {

   public static final String JBOSS_UNIVERSE_GROUP_ID = "org.jboss.universe";
   public static final String JBOSS_UNIVERSE_ARTIFACT_ID = "community-universe";

    static {
      enableJBossLogManager();
   }

   private static void enableJBossLogManager() {
      if (System.getProperty("java.util.logging.manager") == null) {
         System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
      }
   }

   public static void main(String[] args) throws ProvisioningException, IOException {
      if (args.length < 3) {
         System.out.println("Not enough parameters. Need to provide FPL, output directory and channels file.");
         return;
      }
      final String fpl = args[0];
      final String base = args[1];
      final String channelsFile = args[2];

      new GalleonProvision().installFeaturePack(fpl, base, channelsFile);
    }

    public void installFeaturePack(String fpl, String path, String channelsFile) throws ProvisioningException, IOException {
        final RepositorySystem repoSystem = newRepositorySystem();
        Path installDir = Paths.get(path);
        if (Files.exists(installDir)) {
            throw new ProvisioningException("Installation dir " + installDir + " already exists");
        }
        final ChannelMavenArtifactRepositoryManager maven
                = new ChannelMavenArtifactRepositoryManager(repoSystem, MavenFallback.getDefaultRepositorySystemSession(repoSystem),
                        MavenFallback.buildRepositories(), Paths.get(channelsFile), false, null);
        try {
            ProvisioningManager provMgr = ProvisioningManager.builder().addArtifactResolver(maven)
                    .setInstallationHome(installDir).build();
            FeaturePackLocation loc = FeaturePackLocation.fromString(fpl);
            provMgr.install(loc);
            maven.done(installDir);
        } finally {
            maven.close();
        }
    }

   private RepositorySystem newRepositorySystem()
   {
      /*
       * Aether's components implement org.eclipse.aether.spi.locator.Service to ease manual wiring and using the
       * prepopulated DefaultServiceLocator, we only need to register the repository connector and transporter
       * factories.
       */
      DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
      locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class );
      locator.addService(TransporterFactory.class, FileTransporterFactory.class );
      locator.addService( TransporterFactory.class, HttpTransporterFactory.class );

      locator.setErrorHandler( new DefaultServiceLocator.ErrorHandler()
      {
         @Override
         public void serviceCreationFailed( Class<?> type, Class<?> impl, Throwable exception )
         {
            System.out.println(String.format("Service creation failed for %s with implementation %s",
                                             type, impl ));
            exception.printStackTrace();
         }
      } );

      return locator.getService( RepositorySystem.class );
    }

}
