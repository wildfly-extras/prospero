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

    public void print(List<License> pendingLicenses) {
        if (!pendingLicenses.isEmpty()) {
            boolean first = true;
            for (License pendingLicense : pendingLicenses) {
                if (!first) {
                    System.out.println();
                }
                first = false;

                System.out.println("===============");
                System.out.println(pendingLicense.getTitle());
                System.out.println("===============");
                final String text = pendingLicense.getText();
                final String[] lines = text.split("\n");
                for (String line : lines) {
                    System.out.println("  " + WordUtils.wrap(line, 118, System.lineSeparator() + "  ", true));
                }
                System.out.println("===============");
            }
            System.out.println();
        }
    }
}
