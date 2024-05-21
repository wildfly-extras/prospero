/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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

import org.apache.commons.text.WordUtils;
import org.wildfly.prospero.licenses.License;

import java.util.List;

public class LicensePrinter {

    private final CliConsole console;

    public LicensePrinter(CliConsole console) {
        this.console = console;
    }

    public void print(List<License> pendingLicenses) {
        if (!pendingLicenses.isEmpty()) {
            boolean first = true;
            for (License pendingLicense : pendingLicenses) {
                if (!first) {
                    console.println("");
                }
                first = false;

                console.println("===============");
                console.println(pendingLicense.getTitle());
                console.println("===============");
                final String text = pendingLicense.getText();
                final String[] lines = text.split("\n");
                for (String line : lines) {
                    console.println("  " + WordUtils.wrap(line, 118, System.lineSeparator() + "  ", true));
                }
                console.println("===============");
            }
            console.println("");
        }
    }
}
