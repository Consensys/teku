/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.cli;

public class OSUtils {

  public static final String CR = System.getProperty("line.separator");
  public static final String SLASH = System.getProperty("file.separator");
  public static final boolean IS_WIN = System.getProperty("os.name").toLowerCase().contains("win");

  public static String toOSPath(String nixPath) {
    if (IS_WIN) {
      String ret = nixPath.replace('/', '\\');
      if (nixPath.startsWith("/")) {
        ret = "C:" + ret;
      }
      return ret;
    } else {
      return nixPath;
    }
  }
}
