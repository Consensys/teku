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

package tech.pegasys.artemis.beaconrestapi.schema;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

public class BeaconChainHeadResponse {
  public final UnsignedLong headSlot;
  public final UnsignedLong headEpoch;
  public final Bytes32 headBlockRoot;

  public final UnsignedLong finalizedSlot;
  public final UnsignedLong finalizedEpoch;
  public final Bytes32 finalizedBlockRoot;

  public final UnsignedLong justifiedSlot;
  public final UnsignedLong justifiedEpoch;
  public final Bytes32 justifiedBlockRoot;

  public BeaconChainHeadResponse(
      UnsignedLong headSlot,
      UnsignedLong headEpoch,
      Bytes32 headBlockRoot,
      UnsignedLong finalizedSlot,
      UnsignedLong finalizedEpoch,
      Bytes32 finalizedBlockRoot,
      UnsignedLong justifiedSlot,
      UnsignedLong justifiedEpoch,
      Bytes32 justifiedBlockRoot) {
    this.headSlot = headSlot;
    this.headEpoch = headEpoch;
    this.headBlockRoot = headBlockRoot;

    this.finalizedSlot = finalizedSlot;
    this.finalizedEpoch = finalizedEpoch;
    this.finalizedBlockRoot = finalizedBlockRoot;

    this.justifiedSlot = justifiedSlot;
    this.justifiedEpoch = justifiedEpoch;
    this.justifiedBlockRoot = justifiedBlockRoot;
  }
}
