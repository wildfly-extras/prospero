/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.prospero.cli;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;

import org.jboss.galleon.progresstracking.ProgressCallback;
import org.jboss.galleon.progresstracking.ProgressTracker;
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
                        getStdOut().print(CliMessages.MESSAGES.resolvingFeaturePack());
                        break;
                    case "PACKAGES":
                        getStdOut().print(CliMessages.MESSAGES.installingPackages());
                        break;
                    case "CONFIGS":
                        getStdOut().print(CliMessages.MESSAGES.generatingConfiguration());
                        break;
                    case "JBMODULES":
                        getStdOut().print(CliMessages.MESSAGES.installingJBossModules());
                        break;
                }
            }

            @Override
            public void pulse(ProgressTracker tracker) {
                final double progress = tracker.getProgress();
                switch (id) {
                    case "LAYOUT_BUILD":
                        getStdOut().print("\r");
                        getStdOut().printf(CliMessages.MESSAGES.resolvingFeaturePack() + " %.0f%%", progress);
                        break;
                    case "PACKAGES":
                        getStdOut().print("\r");
                        getStdOut().printf(CliMessages.MESSAGES.installingPackages() + " %.0f%%", progress);
                        break;
                    case "CONFIGS":
                        getStdOut().print("\r");
                        getStdOut().printf(CliMessages.MESSAGES.generatingConfiguration() + " %.0f%%", progress);
                        break;
                    case "JBMODULES":
                        getStdOut().print("\r");
                        getStdOut().printf(CliMessages.MESSAGES.installingJBossModules() + " %.0f%%", progress);
                        break;
                }
            }

            @Override
            public void complete(ProgressTracker tracker) {
                switch (id) {
                    case "LAYOUT_BUILD":
                        getStdOut().print("\r");
                        getStdOut().println(CliMessages.MESSAGES.featurePacksResolved());
                        break;
                    case "PACKAGES":
                        getStdOut().print("\r");
                        getStdOut().println(CliMessages.MESSAGES.packagesInstalled());
                        break;
                    case "CONFIGS":
                        getStdOut().print("\r");
                        getStdOut().println(CliMessages.MESSAGES.configurationsGenerated());
                        break;
                    case "JBMODULES":
                        getStdOut().print("\r");
                        getStdOut().println(CliMessages.MESSAGES.jbossModulesInstalled());
                        break;
                }
            }
        };
    }

    @Override
    public void updatesFound(List<ArtifactChange> artifactUpdates) {
        if (artifactUpdates.isEmpty()) {
            getStdOut().println(CliMessages.MESSAGES.noUpdatesFound());
        } else {
            getStdOut().println(CliMessages.MESSAGES.updatesFound());
            for (ArtifactChange artifactUpdate : artifactUpdates) {
                final Optional<String> newVersion = artifactUpdate.getNewVersion();
                final Optional<String> oldVersion = artifactUpdate.getOldVersion();
                final String artifactName = artifactUpdate.getArtifactName();

                getStdOut().printf("  %s%-50s    %-20s ==>  %-20s%n", artifactUpdate.isDowngrade()?"[*]":"", artifactName, oldVersion.orElse("[]"),
                        newVersion.orElse("[]"));
            }

            if (artifactUpdates.stream().anyMatch(ArtifactChange::isDowngrade)) {
                getStdOut().printf(CliMessages.MESSAGES.possibleDowngrade());
            }
        }
    }

    @Override
    public boolean confirmUpdates() {
        return confirm(CliMessages.MESSAGES.continueWithUpdate(),
                CliMessages.MESSAGES.applyingUpdates(),
                CliMessages.MESSAGES.updateCancelled());
    }

    @Override
    public boolean confirm(String prompt, String accepted, String cancelled) {
        getStdOut().print(prompt);
        Scanner sc = new Scanner(getInput());
        while (true) {
            String resp = sc.nextLine();
            if (resp.equalsIgnoreCase(CliMessages.MESSAGES.noShortcut()) || resp.isBlank()) {
                println(cancelled);
                return false;
            } else if (resp.equalsIgnoreCase(CliMessages.MESSAGES.yesShortcut())) {
                println(accepted);
                return true;
            } else {
                getStdOut().print(CliMessages.MESSAGES.chooseYN());
            }
        }
    }

    @Override
    public void updatesComplete() {
        println(CliMessages.MESSAGES.updateComplete());
    }

}
