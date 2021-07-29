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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.redhat.prospero.cli.GalleonProgressCallback;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.jboss.galleon.DefaultMessageWriter;
import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.UniverseSpec;
import org.jboss.galleon.universe.maven.MavenUniverseFactory;

public class GalleonTest {

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
      if (args.length < 1) {
         System.out.println("Not enough parameters. Need to provide WFLY installation.");
         return;
      }
      final String base = args[0];
      final String channelsFile = args[1];

      installCore(base, channelsFile);
   }

   public static void installCore(String path, String channelsFile) throws ProvisioningException, IOException {
      final ProvisioningLayoutFactory layoutFactory = getLayoutFactory();
      addProgressCallbacks(layoutFactory);

      final ProvisioningManager.Builder builder = ProvisioningManager.builder();
      builder.setLayoutFactory(layoutFactory);
      builder.setInstallationHome(Paths.get(path));

      final ProvisioningManager provMgr = builder.build();
      FeaturePackLocation loc = FeaturePackLocation.fromString("wildfly-core:16/snapshot");
      loc = loc.replaceUniverse(new UniverseSpec(MavenUniverseFactory.ID,
                       JBOSS_UNIVERSE_GROUP_ID + ":" + JBOSS_UNIVERSE_ARTIFACT_ID));

      Map<String, String> params = new HashMap<>();
      params.put("use-prospero", "true");
      params.put("prospero-channels-file", channelsFile);
      provMgr.install(loc, params);
   }

   private static void addProgressCallbacks(ProvisioningLayoutFactory layoutFactory) {
      layoutFactory.setProgressCallback("LAYOUT_BUILD", new GalleonProgressCallback<FeaturePackLocation.FPID>("Resolving feature-pack", "Feature-packs resolved."));
      layoutFactory.setProgressCallback("PACKAGES", new GalleonProgressCallback<FeaturePackLocation.FPID>("Installing packages", "Packages installed."));
      layoutFactory.setProgressCallback("CONFIGS", new GalleonProgressCallback<FeaturePackLocation.FPID>("Generating configuration", "Configurations generated."));
      layoutFactory.setProgressCallback("JBMODULES", new GalleonProgressCallback<FeaturePackLocation.FPID>("Installing JBoss modules", "JBoss modules installed."));
   }

   private static ProvisioningLayoutFactory getLayoutFactory() throws ProvisioningException, IOException {
      final RepositorySystem repoSystem = newRepositorySystem();
      List<RemoteRepository> repos = new ArrayList<>();
      final RemoteRepository.Builder repoBuilder = new RemoteRepository.Builder("dev", "default", "http://localhost:8081/repository/dev/");
      repos.add(repoBuilder.build());
      final MavenArtifactRepositoryManager maven = new MavenArtifactRepositoryManager(repoSystem, newRepositorySystemSession(repoSystem), repos);

      final UniverseResolver resolver = UniverseResolver.builder().addArtifactResolver(maven).build();
      return ProvisioningLayoutFactory.getInstance(resolver);
   }

   private static RepositorySystem newRepositorySystem()
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

   private static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system ) throws IOException {
      DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

      org.eclipse.aether.repository.LocalRepository localRepo = new LocalRepository(Files.createTempDirectory("mvn-repo").toString() );
      session.setLocalRepositoryManager( system.newLocalRepositoryManager( session, localRepo ) );

      return session;
   }

}
