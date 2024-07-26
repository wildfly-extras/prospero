/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
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

package org.wildfly.prospero.licenses;

import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

class SortedProperties extends Properties {
    @Override
    public Enumeration<Object> keys() {
        return Collections.enumeration(keySet());
    }

    @Override
    public Set<Object> keySet() {
        final Set<Object> keyList = new TreeSet<>(Comparator.comparing(Object::toString));
        keyList.addAll(super.keySet());
        return keyList;
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        final TreeSet<Map.Entry<Object, Object>> treeSet = new TreeSet<>((o1, o2) -> o1.getKey().toString().compareTo(o2.toString()));
        treeSet.addAll(super.entrySet());
        return treeSet;
    }
}
