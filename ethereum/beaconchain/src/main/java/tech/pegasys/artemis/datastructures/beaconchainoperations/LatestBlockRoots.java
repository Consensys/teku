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

package tech.pegasys.artemis.datastructures.beaconchainoperations;

import com.google.common.primitives.UnsignedLong;
import java.util.LinkedHashMap;
import java.util.Map;
import net.consensys.cava.bytes.Bytes32;
import tech.pegasys.artemis.Constants;

public class LatestBlockRoots extends LinkedHashMap<UnsignedLong, Bytes32> {

  @Override
  protected boolean removeEldestEntry(Map.Entry<UnsignedLong, Bytes32> eldest) {
    return this.size() > Constants.LATEST_BLOCK_ROOTS_LENGTH;
  }
}
