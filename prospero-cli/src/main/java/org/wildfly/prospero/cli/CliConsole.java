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

package org.wildfly.prospero.cli;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import org.jboss.galleon.layout.FeaturePackUpdatePlan;
import org.jboss.galleon.progresstracking.ProgressCallback;
import org.jboss.galleon.progresstracking.ProgressTracker;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.wildfly.prospero.actions.Console;
import org.wildfly.prospero.api.ArtifactChange;

public class CliConsole implements Console {

    public static final int PULSE_INTERVAL = 500;
    public static final int PULSE_PCT = 5;

    @Override
    public void installationComplete() {

    }

    @Override
    public ProgressCallback<?> getProgressCallback(String id) {
        return new ProgressCallback<>() {

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
                        getStdOut().print("Resolving feature-pack");
                        break;
                    case "PACKAGES":
                        getStdOut().print("Installing packages");
                        break;
                    case "CONFIGS":
                        getStdOut().print("Generating configuration");
                        break;
                    case "JBMODULES":
                        getStdOut().print("Installing JBoss modules");
                        break;
                }
            }

            @Override
            public void pulse(ProgressTracker tracker) {
                final double progress = tracker.getProgress();
                switch (id) {
                    case "LAYOUT_BUILD":
                        getStdOut().print("\r");
                        getStdOut().printf("Resolving feature-pack %.0f%%", progress);
                        break;
                    case "PACKAGES":
                        getStdOut().print("\r");
                        getStdOut().printf("Installing packages %.0f%%", progress);
                        break;
                    case "CONFIGS":
                        getStdOut().print("\r");
                        getStdOut().printf("Generating configuration %.0f%%", progress);
                        break;
                    case "JBMODULES":
                        getStdOut().print("\r");
                        getStdOut().printf("Installing JBoss modules %.0f%%", progress);
                        break;
                }
            }

            @Override
            public void complete(ProgressTracker tracker) {
                switch (id) {
                    case "LAYOUT_BUILD":
                        getStdOut().print("\r");
                        getStdOut().println("Feature-packs resolved.");
                        break;
                    case "PACKAGES":
                        getStdOut().print("\r");
                        getStdOut().println("Packages installed.");
                        break;
                    case "CONFIGS":
                        getStdOut().print("\r");
                        getStdOut().println("Configurations generated.");
                        break;
                    case "JBMODULES":
                        getStdOut().print("\r");
                        getStdOut().println("JBoss modules installed.");
                        break;
                }
            }
        };
    }

    @Override
    public void updatesFound(Collection<FeaturePackUpdatePlan> fpUpdates, List<ArtifactChange> artifactUpdates) {
        if (fpUpdates.isEmpty() && artifactUpdates.isEmpty()) {
            getStdOut().println("No updates found");
        } else {
            getStdOut().println("Updates found:");
            for (FeaturePackUpdatePlan fpUpdate : fpUpdates) {
                final FeaturePackLocation oldFp = fpUpdate.getInstalledLocation();
                final FeaturePackLocation newFp = fpUpdate.getNewLocation();
                getStdOut().printf("  %-40s    %-20s ==>  %-20s%n", newFp.getProducerName(), oldFp.getBuild(), newFp.getBuild());
            }
            for (ArtifactChange artifactUpdate : artifactUpdates) {
                final Optional<String> newVersion = artifactUpdate.getNewVersion();
                final Optional<String> oldVersion = artifactUpdate.getOldVersion();
                final String artifactName = artifactUpdate.getArtifactName();

                getStdOut().printf("  %-40s    %-20s ==>  %-20s%n", artifactName, oldVersion.orElse("[]"), newVersion.orElse("[]"));
            }
        }
    }

    @Override
    public boolean confirmUpdates() {
        getStdOut().print("Continue with update [y/n]: ");
        Scanner sc = new Scanner(getInput());
        while (true) {
            String resp = sc.nextLine();
            if (resp.equalsIgnoreCase("n")) {
                println("Update cancelled");
                return false;
            } else if (resp.equalsIgnoreCase("y")) {
                println("Applying updates");
                return true;
            } else {
                getStdOut().print("Choose [y/n]: ");
            }
        }
    }

    @Override
    public void updatesComplete() {
        println("Update complete!");
    }

}
