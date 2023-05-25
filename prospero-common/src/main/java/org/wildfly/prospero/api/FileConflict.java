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

import org.jboss.galleon.diff.FsDiff;

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

    public static AddBuilder userAdded(String path) {
        return new AddBuilder(path);
    }

    public static RemoveBuilder userRemoved(String path) {
        return new RemoveBuilder(path);
    }

    public static ModifyBuilder userModified(String path) {
        return new ModifyBuilder(path);
    }

    public static class ModifyBuilder {
        private final String path;

        private ModifyBuilder(String path) {
            this.path = path;
        }

        public ResolutionBuilder updateModified() {
            return new ResolutionBuilder(path, Change.MODIFIED, Change.MODIFIED);
        }

        public ResolutionBuilder updateDidntChange() {
            return new ResolutionBuilder(path, Change.MODIFIED, Change.NONE);
        }

        public ResolutionBuilder updateRemoved() {
            return new ResolutionBuilder(path, Change.MODIFIED, Change.REMOVED);
        }
    }

    public static class RemoveBuilder {
        private final String path;

        private RemoveBuilder(String path) {
            this.path = path;
        }

        public ResolutionBuilder updateModified() {
            return new ResolutionBuilder(path, Change.REMOVED, Change.MODIFIED);
        }

        public ResolutionBuilder updateDidntChange() {
            return new ResolutionBuilder(path, Change.REMOVED, Change.NONE);
        }
    }

    public static class AddBuilder {

        private final String path;

        private AddBuilder(String path) {
            this.path = path;
        }

        public ResolutionBuilder updateAdded() {
            return new ResolutionBuilder(path, Change.ADDED, Change.ADDED);
        }

        public ResolutionBuilder updateDidntChange() {
            return new ResolutionBuilder(path, Change.ADDED, Change.NONE);
        }
    }

    public static class ResolutionBuilder {

        private final Change user;
        private final Change update;
        private final String path;

        private ResolutionBuilder(String path, Change user, Change update) {
            this.user = user;
            this.update = update;
            this.path = path;
        }

        public FileConflict userPreserved() {
            return new FileConflict(user, update, Resolution.USER, path);
        }

        public FileConflict overwritten() {
            return new FileConflict(user, update, Resolution.UPDATE, path);
        }
    }

    private FileConflict(Change userChange, Change updateChange, Resolution resolution, String relativePath) {
        this.userChange = userChange;
        this.updateChange = updateChange;
        this.resolution = resolution;
        this.relativePath = relativePath;
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

    public String prettyPrint() {
        String status;
        if (getResolution() == Resolution.UPDATE) {
            status = "!" + FsDiff.FORCED;
        } else {
            if (getUserChange() == getUpdateChange()) {
                status = "!" + FsDiff.CONFLICT;
            } else if (getUserChange() == Change.MODIFIED && getUpdateChange() == Change.REMOVED) {
                status = "!" + FsDiff.MODIFIED;
            } else {
                switch (getUserChange()) {
                    case MODIFIED:
                        status = " " + FsDiff.MODIFIED;
                        break;
                    case ADDED:
                        status = " " + FsDiff.ADDED;
                        break;
                    case REMOVED:
                        status = " " + FsDiff.REMOVED;
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected Change " + this.toString());
                }
            }
        }
        return status + " " + getRelativePath();
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
