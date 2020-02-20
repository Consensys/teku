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

package tech.pegasys.artemis.test.acceptance.dsl.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

public class BeaconChainHead {
  public final UnsignedLong headSlot;
  public final UnsignedLong headEpoch;
  public final Bytes32 headBlockRoot;
  public final UnsignedLong finalizedSlot;
  public final UnsignedLong finalizedEpoch;
  public final Bytes32 finalizedBlockRoot;
  public final UnsignedLong justifiedSlot;
  public final UnsignedLong justifiedEpoch;
  public final Bytes32 justifiedBlockRoot;
  public final UnsignedLong previousJustifiedSlot;
  public final UnsignedLong previousJustifiedEpoch;
  public final Bytes32 previousJustifiedBlockRoot;

  @JsonCreator
  public BeaconChainHead(
      @JsonProperty("headSlot") UnsignedLong headSlot,
      @JsonProperty("headEpoch") UnsignedLong headEpoch,
      @JsonProperty("headBlockRoot") Bytes32 headBlockRoot,
      @JsonProperty("finalizedSlot") UnsignedLong finalizedSlot,
      @JsonProperty("finalizedEpoch") UnsignedLong finalizedEpoch,
      @JsonProperty("finalizedBlockRoot") Bytes32 finalizedBlockRoot,
      @JsonProperty("justifiedSlot") UnsignedLong justifiedSlot,
      @JsonProperty("justifiedEpoch") UnsignedLong justifiedEpoch,
      @JsonProperty("justifiedBlockRoot") Bytes32 justifiedBlockRoot,
      @JsonProperty("previousJustifiedSlot") UnsignedLong previousJustifiedSlot,
      @JsonProperty("previousJustifiedEpoch") UnsignedLong previousJustifiedEpoch,
      @JsonProperty("previousJustifiedBlockRoot") Bytes32 previousJustifiedBlockRoot) {
    this.headSlot = headSlot;
    this.headEpoch = headEpoch;
    this.headBlockRoot = headBlockRoot;

    this.finalizedSlot = finalizedSlot;
    this.finalizedEpoch = finalizedEpoch;
    this.finalizedBlockRoot = finalizedBlockRoot;

    this.justifiedSlot = justifiedSlot;
    this.justifiedEpoch = justifiedEpoch;
    this.justifiedBlockRoot = justifiedBlockRoot;

    this.previousJustifiedSlot = previousJustifiedSlot;
    this.previousJustifiedEpoch = previousJustifiedEpoch;
    this.previousJustifiedBlockRoot = previousJustifiedBlockRoot;
  }
}
