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

package org.wildfly.prospero.api;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public class SavedState {

    public enum Type { UPDATE, INSTALL, ROLLBACK, CONFIG_CHANGE;}

    private String hash;
    private Instant timestamp;
    private Type type;

    public SavedState(String hash, Instant timestamp, Type type) {
        this.hash = hash;
        this.timestamp = timestamp;
        this.type = type;
    }

    public SavedState(String hash) {
        this.hash = hash;
        this.timestamp = null;
        this.type = null;
    }

    public Type getType() {
        return type;
    }

    public String getName() {
        return this.hash;
    }

    public String shortDescription() {
        return String.format("[%s] %s - %s", hash, timestamp.toString(), type.toString().toLowerCase(Locale.ROOT));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SavedState that = (SavedState) o;
        return Objects.equals(hash, that.hash) && Objects.equals(timestamp, that.timestamp) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hash, timestamp, type);
    }
}
