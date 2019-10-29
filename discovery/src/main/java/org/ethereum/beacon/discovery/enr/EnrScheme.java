/*
 * Copyright 2019 ConsenSys AG.
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

package org.ethereum.beacon.discovery.enr;

import java.util.HashMap;
import java.util.Map;

/** Available identity schemes of {@link NodeRecord} */
public enum EnrScheme {
  V4("v4");

  private static final Map<String, EnrScheme> nameMap = new HashMap<>();

  static {
    for (EnrScheme scheme : EnrScheme.values()) {
      nameMap.put(scheme.name, scheme);
    }
  }

  private String name;

  private EnrScheme(String name) {
    this.name = name;
  }

  public static EnrScheme fromString(String name) {
    return nameMap.get(name);
  }

  public String stringName() {
    return name;
  }
}
