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

package org.ethereum.beacon.discovery.schema;

import java.util.HashMap;
import java.util.Map;

public enum NodeStatus {
  ACTIVE(0x01),
  SLEEP(0x02),
  DEAD(0x03);

  private static final Map<Integer, NodeStatus> codeMap = new HashMap<>();

  static {
    for (NodeStatus type : NodeStatus.values()) {
      codeMap.put(type.code, type);
    }
  }

  private int code;

  NodeStatus(int code) {
    this.code = code;
  }

  public static NodeStatus fromNumber(int i) {
    return codeMap.get(i);
  }

  public byte byteCode() {
    return (byte) code;
  }
}
