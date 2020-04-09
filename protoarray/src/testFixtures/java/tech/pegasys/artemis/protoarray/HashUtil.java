/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.artemis.protoarray;

import static tech.pegasys.artemis.datastructures.util.BeaconStateUtil.int_to_bytes32;

import org.apache.tuweni.bytes.Bytes32;

public class HashUtil {

  // Gives a deterministic hash for a given integer
  public static Bytes32 getHash(int i) {
    return int_to_bytes32(Integer.toUnsignedLong(i + 1));
  }
}
