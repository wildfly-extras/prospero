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
            String nameText = diff.getName().map(s -> s + ":\t\t").orElse("");
            if (nested) {
                System.out.printf("%s%s ==> %s%n", nameText, diff.getOldValue().orElse("[]"), diff.getNewValue().orElse("[]"));
            } else {
                System.out.printf("[%s] %s%s ==> %s%n", getStatus(diff), nameText, diff.getOldValue().orElse("[]"), diff.getNewValue().orElse("[]"));
            }
        } else {
            String nameText = diff.getName().orElse("");
            if (nested) {
                System.out.printf("%s: %n", nameText);
            } else {
                System.out.printf("[%s] %s:%n", getStatus(diff), nameText);
            }
            diff.getChildren().forEach(c -> print(c, tab + "  ", true));
        }
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
        } else {
            diffType = null;
        }

        return String.format("%s%s", statusText, diffType!=null ? " "+diffType : "");
    }
}
