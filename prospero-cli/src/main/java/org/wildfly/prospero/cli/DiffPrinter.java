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

import org.wildfly.prospero.api.ArtifactChange;
import org.wildfly.prospero.api.ChannelChange;
import org.wildfly.prospero.api.Diff;
import org.wildfly.prospero.api.FeatureChange;

public class DiffPrinter {

    private final String initialTab;

    public DiffPrinter(String initialTab) {
        this.initialTab = initialTab;
    }

    public void print(Diff diff) {
        print(diff, initialTab, false);
    }

    private void print(Diff diff, String tab, boolean nested) {
        System.out.print(tab);


        if (diff.getChildren().isEmpty()) {
            if (diff.hasValues()) {
                String nameText = diff.getName().map(s -> s + ":\t\t").orElse("");
                print(diff, nested, "%s%s ==> %s%n", nameText, diff.getOldValue().orElse("[]"), diff.getNewValue().orElse("[]"));
            } else {
                String nameText = diff.getName().map(s -> s + "\t\t").orElse("");
                print(diff, nested, "%s%n", nameText);
            }
        } else {
            String nameText = diff.getName().orElse("");
            print(diff, nested, "%s: %n", nameText);
            diff.getChildren().forEach(c -> print(c, tab + "  ", true));
        }
    }

    private static void print(Diff diff, boolean nested, String text, String... args) {
        if (!nested) {
            text = String.format("[%s] ", getStatus(diff)) + String.format(text, (String[]) args);
        } else {
            text = String.format(text, (String[]) args);
        }
        System.out.print(text);
    }

    private static String getStatus(Diff diff) {
        Diff.Status status = diff.getStatus();
        String statusText;
        switch (status) {
            case ADDED:
                statusText = CliMessages.MESSAGES.changeAdded();
                break;
            case REMOVED:
                statusText = CliMessages.MESSAGES.changeRemoved();
                break;
            case MODIFIED:
                statusText = CliMessages.MESSAGES.changeUpdated();
                break;
            default:
                throw new RuntimeException("Unknown status: " + status);
        }

        String diffType;
        if (diff instanceof ArtifactChange) {
            diffType = CliMessages.MESSAGES.artifactChangeType();
        } else if (diff instanceof ChannelChange) {
            diffType = CliMessages.MESSAGES.channelChangeType();
        } else if (diff instanceof FeatureChange) {
            final FeatureChange.Type subType = ((FeatureChange) diff).getType();
            switch (subType) {
                case FEATURE:
                    diffType = CliMessages.MESSAGES.featurePackTitle();
                    break;
                case CONFIG:
                    diffType = CliMessages.MESSAGES.configurationModel();
                    break;
                default:
                    diffType = subType.name();
            }
        } else {
            diffType = null;
        }

        return String.format("%s%s", statusText, diffType!=null ? " "+diffType : "");
    }
}
