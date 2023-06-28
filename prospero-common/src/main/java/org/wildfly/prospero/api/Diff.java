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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Diff {
    private Optional<String> name;
    private Optional<String> oldValue;
    private Optional<String> newValue;
    private List<Diff> children;

    public Optional<String> getName() {
        return name;
    }

    public List<Diff> getChildren() {
        return children;
    }

    public Optional<String> getOldValue() {
        return oldValue;
    }

    public Optional<String> getNewValue() {
        return newValue;
    }

    public Status getStatus() {
        return status;
    }

    public Optional<Diff> getChild(String name) {
        return children.stream().filter(c->c.getName().orElse("").equals(name)).findFirst();
    }

    /**
     * checks if the record holds any non-null values.
     *
     * @return true if either {@code newValue} or {@code oldValue} has a non-null value.
     */
    public boolean hasValues() {
        return !newValue.isEmpty() || !oldValue.isEmpty();
    }

    public enum Status {ADDED, REMOVED, MODIFIED}

    private final Status status;

    public Diff(String name, String oldValue, String newValue) {
        this.name = Optional.ofNullable(name);
        this.oldValue = Optional.ofNullable(oldValue);
        this.newValue = Optional.ofNullable(newValue);
        this.children = Collections.emptyList();

        if (this.oldValue.isEmpty()) {
            status = Status.ADDED;
        } else if (this.newValue.isEmpty()) {
            status = Status.REMOVED;
        } else {
            status = Status.MODIFIED;
        }
    }

    public Diff(String name, Status status, Diff... nested) {
        this(name, status, List.of(nested));
    }

    public Diff(String name, Status status, List<Diff> nested) {
        this.name = Optional.ofNullable(name);
        this.status = status;

        this.oldValue = Optional.empty();
        this.newValue = Optional.empty();
        this.children = nested;
    }

    @Override
    public String toString() {
        return "Diff{" +
                "name=" + name +
                ", oldValue=" + oldValue +
                ", newValue=" + newValue +
                ", children=" + children +
                ", status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Diff diff = (Diff) o;
        return Objects.equals(name, diff.name) && Objects.equals(oldValue, diff.oldValue) && Objects.equals(newValue, diff.newValue) && Objects.equals(children, diff.children) && status == diff.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, oldValue, newValue, children, status);
    }
}
