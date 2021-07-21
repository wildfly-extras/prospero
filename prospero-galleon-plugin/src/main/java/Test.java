import java.io.File;
import java.io.IOException;
import java.util.Arrays;

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

public class Test {

   public static void main(String[] args) {
      String name = "name.é";

      final String sanitized = sanitizeFileName(name);

      System.out.println(sanitized.equals(name));
      System.out.println("name: " + name);
      System.out.println("sanitized: " + sanitized);
   }

   static final int[] ILLEGAL_CHARS = {34, 60, 62, 124, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 58, 42, 63, 92, 47};
   static {
      Arrays.sort(ILLEGAL_CHARS);
   }

   static String sanitizeFileName(String name) {
      if(name == null || name.isEmpty()) {
         return "";
      }
      StringBuilder cleanName = new StringBuilder();
      int len = name.codePointCount(0, name.length());
      for (int i = 0; i < len; i++) {
         int c = name.codePointAt(i);
         if (Arrays.binarySearch(ILLEGAL_CHARS, c) < 0) {
            cleanName.appendCodePoint(c);
         }
      }
      try {
         return new File(cleanName.toString()).getCanonicalFile().getName();
      } catch (IOException ex) {
         return "";
      }
   }

}
