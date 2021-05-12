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

package com.redhat.prospero.api;

import java.util.ArrayList;

public class ArtifactDependencies extends Gav {

   private final ArrayList<Artifact> dependencies;

   public ArtifactDependencies(String groupId, String artifactId, String version, String classifier, ArrayList<Artifact> dependencies) {
      super(groupId, artifactId, version, classifier, "jar");
      this.dependencies = dependencies;
   }

   @Override
   public Gav newVersion(String newVersion) {
      return new ArtifactDependencies(groupId, artifactId, newVersion, classifier, dependencies);
   }

   public ArrayList<Artifact> getDependencies() {
      return dependencies;
   }
}
