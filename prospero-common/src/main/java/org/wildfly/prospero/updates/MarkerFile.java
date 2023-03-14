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

package org.wildfly.prospero.updates;

import org.wildfly.prospero.actions.ApplyCandidateAction;
import org.wildfly.prospero.metadata.ProsperoMetadataUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

public class MarkerFile {
    public static final Path UPDATE_MARKER_FILE = Path.of(ProsperoMetadataUtils.METADATA_DIR, ".candidate.txt");
    private static final String STATE_PROPERTY = "state";
    private static final String OPERATION_PROPERTY = "operation";
    private final String state;
    private final ApplyCandidateAction.Type operation;

    public MarkerFile(String state, ApplyCandidateAction.Type operation) {
        this.state = state;
        this.operation = operation;
    }

    public String getState() {
        return state;
    }

    public ApplyCandidateAction.Type getOperation() {
        return operation;
    }

    public static MarkerFile read(Path serverPath) throws IOException {
        final Properties properties = new Properties();
        try (final FileInputStream fis = new FileInputStream(serverPath.resolve(UPDATE_MARKER_FILE).toFile())) {
            properties.load(fis);
        }
        final ApplyCandidateAction.Type type = ApplyCandidateAction.Type.from(properties.getProperty(OPERATION_PROPERTY));
        return new MarkerFile(properties.getProperty(STATE_PROPERTY), type);
    }

    public void write(Path targetPath) throws IOException {
        final Properties properties = new Properties();
        properties.setProperty(STATE_PROPERTY, state);
        properties.setProperty(OPERATION_PROPERTY, operation.getText());
        try (final FileOutputStream fos = new FileOutputStream(targetPath.resolve(UPDATE_MARKER_FILE).toFile())) {
            properties.store(fos, null);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MarkerFile that = (MarkerFile) o;
        return Objects.equals(state, that.state) && Objects.equals(operation, that.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, operation);
    }

    @Override
    public String toString() {
        return "MarkerFile{" +
                "state='" + state + '\'' +
                ", operation='" + operation + '\'' +
                '}';
    }


}
