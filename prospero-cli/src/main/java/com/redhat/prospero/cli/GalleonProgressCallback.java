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

package com.redhat.prospero.cli;

import org.jboss.galleon.progresstracking.ProgressCallback;
import org.jboss.galleon.progresstracking.ProgressTracker;
import org.jboss.galleon.universe.FeaturePackLocation;

public class GalleonProgressCallback<T> implements ProgressCallback<T> {

   private final String start;
   private final String end;

   public GalleonProgressCallback(String start, String end) {
      this.start = start;
      this.end = end;
   }

   @Override
   public void starting(ProgressTracker<T> progressTracker) {
      System.out.println(start);
   }

   @Override
   public void pulse(ProgressTracker<T> progressTracker) {
   }

   @Override
   public void complete(ProgressTracker<T> progressTracker) {
      System.out.println(end);
   }
}
