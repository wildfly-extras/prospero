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
import java.util.List;

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
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.layout.FeaturePackLayout;
import org.jboss.galleon.layout.ProvisioningLayout;
import org.jboss.galleon.layout.ProvisioningLayoutFactory;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.runtime.ProvisioningRuntime;
import org.jboss.galleon.runtime.ProvisioningRuntimeBuilder;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.UniverseSpec;
import org.jboss.galleon.universe.maven.MavenUniverseFactory;

public class GalleonTest {

   public static final String JBOSS_UNIVERSE_GROUP_ID = "org.jboss.universe";
   public static final String JBOSS_UNIVERSE_ARTIFACT_ID = "community-universe";

   public static void main(String[] args) throws ProvisioningException, IOException {
      testSimple();
   }

   public static void testRuntime() throws ProvisioningException, IOException {
      final ProvisioningRuntimeBuilder provisioningRuntimeBuilder = ProvisioningRuntimeBuilder.newInstance();
      final ProvisioningLayoutFactory layoutFactory = getLayoutFactory();
      FeaturePackLocation loc = FeaturePackLocation.fromString("wildfly-core@maven(org.jboss.universe:community-universe):16/snapshot");
//      loc = loc.replaceUniverse(new UniverseSpec(MavenUniverseFactory.ID,
//                                                 JBOSS_UNIVERSE_GROUP_ID + ":" + JBOSS_UNIVERSE_ARTIFACT_ID));
//      System.out.println(loc);
      ProvisioningConfig config = ProvisioningConfig.builder()
                                    .addFeaturePackDep(loc)
                                    .build();
      final ProvisioningLayout<FeaturePackLayout> configLayout = layoutFactory.newConfigLayout(config);
      provisioningRuntimeBuilder.initLayout(configLayout);
      final ProvisioningRuntime runtime = provisioningRuntimeBuilder.build();

      System.out.println(runtime.getStagedDir());
      runtime.provision();
   }

   public static void testSimple() throws ProvisioningException, IOException {
      ProvisioningManager.Builder builder = ProvisioningManager.builder();
      builder.setLayoutFactory(getLayoutFactory());
      builder.setInstallationHome(Paths.get("/Users/spyrkob/workspaces/set/prospero/prospero/gall-test"));
//      builder.setMessageWriter(getMessageWriter(verbose));
      final ProvisioningManager provMgr = builder.build();
      FeaturePackLocation loc = FeaturePackLocation.fromString("wildfly-core:16/snapshot");
      loc = loc.replaceUniverse(new UniverseSpec(MavenUniverseFactory.ID,
                       JBOSS_UNIVERSE_GROUP_ID + ":" + JBOSS_UNIVERSE_ARTIFACT_ID));
      provMgr.install(loc);
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
