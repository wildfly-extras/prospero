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

package com.redhat.prospero.cli;

import java.util.Collection;
import java.util.List;
import java.util.Scanner;

import com.redhat.prospero.api.ArtifactChange;
import org.apache.commons.lang.StringUtils;
import org.eclipse.aether.artifact.Artifact;
import org.jboss.galleon.layout.FeaturePackUpdatePlan;
import org.jboss.galleon.progresstracking.ProgressCallback;
import org.jboss.galleon.progresstracking.ProgressTracker;
import org.jboss.galleon.universe.FeaturePackLocation;

public class CliConsole implements Console {

   public static final int PULSE_INTERVAL = 500;
   public static final int PULSE_PCT = 5;

   @Override
   public void installationComplete() {

   }

   @Override
   public ProgressCallback getProgressCallback(String id) {
      return new ProgressCallback() {

         @Override
         public long getProgressPulsePct() {
            return PULSE_PCT;
         }

         @Override
         public long getMinPulseIntervalMs() {
            return PULSE_INTERVAL;
         }

         @Override
         public long getMaxPulseIntervalMs() {
            return PULSE_INTERVAL;
         }

         @Override
         public void starting(ProgressTracker tracker) {
            switch (id) {
               case "LAYOUT_BUILD":
                  System.out.print("Resolving feature-pack");
                  break;
               case "PACKAGES":
                  System.out.print("Installing packages");
                  break;
               case "CONFIGS":
                  System.out.print("Generating configuration");
                  break;
               case "JBMODULES":
                  System.out.print("Installing JBoss modules");
                  break;
            }
         }

         @Override
         public void pulse(ProgressTracker tracker) {
            final double progress = tracker.getProgress();
            switch (id) {
               case "LAYOUT_BUILD":
                  System.out.print("\r");
                  System.out.printf("Resolving feature-pack %.0f%%", progress);
                  break;
               case "PACKAGES":
                  System.out.print("\r");
                  System.out.printf("Installing packages %.0f%%", progress);
                  break;
               case "CONFIGS":
                  System.out.print("\r");
                  System.out.printf("Generating configuration %.0f%%", progress);
                  break;
               case "JBMODULES":
                  System.out.print("\r");
                  System.out.printf("Installing JBoss modules %.0f%%", progress);
                  break;
            }
         }

         @Override
         public void complete(ProgressTracker tracker) {
            switch (id) {
               case "LAYOUT_BUILD":
                  System.out.print("\r");
                  System.out.println("Feature-packs resolved.");
                  break;
               case "PACKAGES":
                  System.out.print("\r");
                  System.out.println("Packages installed.");
                  break;
               case "CONFIGS":
                  System.out.print("\r");
                  System.out.println("Configurations generated.");
                  break;
               case "JBMODULES":
                  System.out.print("\r");
                  System.out.println("JBoss modules installed.");
                  break;
            }
         }
      };
   }

   @Override
   public void updatesFound(Collection<FeaturePackUpdatePlan> fpUpdates, List<ArtifactChange> artifactUpdates) {
      if (fpUpdates.isEmpty() && artifactUpdates.isEmpty()) {
         System.out.println("No updates found");
      } else {
         System.out.println("Updates found:");
         for (FeaturePackUpdatePlan fpUpdate : fpUpdates) {
             final FeaturePackLocation oldFp = fpUpdate.getInstalledLocation();
             final FeaturePackLocation newFp = fpUpdate.getNewLocation();
             System.out.printf("  %-40s    %-20s ==>  %-20s%n", newFp.getProducerName(), oldFp.getBuild(), newFp.getBuild());
         }
         for (ArtifactChange artifactUpdate : artifactUpdates) {
            final Artifact newArtifact = artifactUpdate.getNewVersion();
            final Artifact oldArtifact = artifactUpdate.getOldVersion();
            final String artifactName;
            if (StringUtils.isEmpty(oldArtifact.getClassifier())) {
               artifactName = String.format("%s:%s", oldArtifact.getGroupId(), oldArtifact.getArtifactId());
            } else {
               artifactName = String.format("%s:%s:%s", oldArtifact.getGroupId(), oldArtifact.getArtifactId(), oldArtifact.getClassifier());
            }
            System.out.printf("  %-40s    %-20s ==>  %-20s%n", artifactName, oldArtifact.getVersion(), newArtifact.getVersion());
         }
      }
   }

   @Override
   public boolean confirmUpdates() {
      System.out.print("Continue with update [y/n]: ");
      Scanner sc = new Scanner(System.in);
      while (true) {
         String resp = sc.nextLine();
         if (resp.equalsIgnoreCase("n")) {
            System.out.println("Update cancelled");
            return false;
         } else if (resp.equalsIgnoreCase("y")) {
            System.out.println("Applying updates");
            return true;
         } else {
            System.out.print("Choose [y/n]: ");
         }
      }
   }

   @Override
   public void updatesComplete() {
      System.out.println("Update complete!");
   }

   @Override
   public void println(String text) {
      System.out.println(text);
   }
}
