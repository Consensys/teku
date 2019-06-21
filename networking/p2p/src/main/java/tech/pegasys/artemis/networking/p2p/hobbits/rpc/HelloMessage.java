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

package tech.pegasys.artemis.networking.p2p.hobbits.rpc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;

public final class HelloMessage {

  private final long networkId;
  private final long chainId;
  private final Bytes32 latestFinalizedRoot;
  private final UInt64 latestFinalizedEpoch;
  private final Bytes32 bestRoot;
  private final UInt64 bestSlot;

  @JsonCreator
  public HelloMessage(
      @JsonProperty("network_id") long networkId,
      @JsonProperty("chain_id") long chainId,
      @JsonProperty("latest_finalized_root") Bytes32 latestFinalizedRoot,
      @JsonProperty("latest_finalized_epoch") UInt64 latestFinalizedEpoch,
      @JsonProperty("best_root") Bytes32 bestRoot,
      @JsonProperty("best_slot") UInt64 bestSlot) {
    this.networkId = networkId;
    this.chainId = chainId;
    this.latestFinalizedRoot = latestFinalizedRoot;
    this.latestFinalizedEpoch = latestFinalizedEpoch;
    this.bestRoot = bestRoot;
    this.bestSlot = bestSlot;
  }

  @JsonProperty("network_id")
  public long networkId() {
    return networkId;
  }

  @JsonProperty("chain_id")
  public long chainId() {
    return chainId;
  }

  @JsonProperty("latest_finalized_root")
  public Bytes32 latestFinalizedRoot() {
    return latestFinalizedRoot;
  }

  @JsonProperty("latest_finalized_epoch")
  public UInt64 latestFinalizedEpoch() {
    return latestFinalizedEpoch;
  }

  @JsonProperty("best_root")
  public Bytes32 bestRoot() {
    return bestRoot;
  }

  @JsonProperty("best_slot")
  public UInt64 bestSlot() {
    return bestSlot;
  }
}
