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

public class Gav {

   protected final String groupId;
   protected final String artifactId;
   protected final String version;
   protected final String classifier;
   protected final String packaging;

   public Gav(String groupId, String artifactId, String version, String classifier, String packaging) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
      this.classifier = classifier;
      this.packaging = packaging;
   }

   public String getGroupId() {
      return groupId;
   }

   public String getArtifactId() {
      return artifactId;
   }

   public String getVersion() {
      return version;
   }

   public String getClassifier() {
      return classifier;
   }

   public String getPackaging() {
      return packaging;
   }

   public String getFileName() {
      if (classifier == null || classifier.length() == 0) {
         return String.format("%s-%s.%s", artifactId, version, packaging);
      } else {
         return String.format("%s-%s-%s.%s", artifactId, version, classifier, packaging);
      }
   }
}
