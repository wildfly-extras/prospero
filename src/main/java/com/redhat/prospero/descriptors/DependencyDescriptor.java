/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.prospero.descriptors;

import java.util.ArrayList;

import com.redhat.prospero.api.Gav;

public class DependencyDescriptor {

   public final String groupId;
   public final String artifactId;
   public final String version;
   public final String classifier;
   public final ArrayList<Dependency> deps;

   public DependencyDescriptor(String groupId, String artifactId, String version, String classifier, ArrayList<Dependency> deps) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
      this.classifier = classifier;
      this.deps = deps;
   }

   public static class Dependency extends Gav {
      public Dependency(String group, String name, String minVersion, String classifier) {
         super(group, name, minVersion, classifier, "jar");
      }
   }
}
