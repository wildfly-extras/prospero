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

package org.wildfly.prospero.api;

import java.util.Objects;

public class FileConflict {

    private Change userChange;
    private Change updateChange;
    private Resolution resolution;
    private String relativePath;

    public enum Change {
        ADDED, MODIFIED, REMOVED, NONE
    }

    public enum Resolution {
        USER, UPDATE
    }

    public FileConflict(Change userChange, Change updateChange, Resolution resolution, String relativePath) {
        this.userChange = userChange;
        this.updateChange = updateChange;
        this.resolution = resolution;
        this.relativePath = relativePath;
    }

    public FileConflict(Change change, Resolution resolution, String relativePath) {
        this(change, change, resolution, relativePath);
    }

    public Change getUserChange() {
        return userChange;
    }

    public Change getUpdateChange() {
        return updateChange;
    }

    public Resolution getResolution() {
        return resolution;
    }

    public String getRelativePath() {
        return relativePath;
    }

    @Override
    public String toString() {
        return "Conflict{" +
                "userChange=" + userChange +
                ", updateChange=" + updateChange +
                ", resolution=" + resolution +
                ", relativePath='" + relativePath + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileConflict conflict = (FileConflict) o;
        return userChange == conflict.userChange && updateChange == conflict.updateChange && resolution == conflict.resolution && Objects.equals(relativePath, conflict.relativePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userChange, updateChange, resolution, relativePath);
    }
}
