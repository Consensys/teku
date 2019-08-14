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
import java.math.BigInteger;

public final class HelloMessage {

  private final short networkId;
  private final short chainId;
  private final byte[] latestFinalizedRoot;
  private final BigInteger latestFinalizedEpoch;
  private final byte[] bestRoot;
  private final BigInteger bestSlot;

  @JsonCreator
  public HelloMessage(
      @JsonProperty("network_id") short networkId,
      @JsonProperty("chain_id") short chainId,
      @JsonProperty("latest_finalized_root") byte[] latestFinalizedRoot,
      @JsonProperty("latest_finalized_epoch") BigInteger latestFinalizedEpoch,
      @JsonProperty("best_root") byte[] bestRoot,
      @JsonProperty("best_slot") BigInteger bestSlot) {
    this.networkId = networkId;
    this.chainId = chainId;
    this.latestFinalizedRoot = latestFinalizedRoot;
    this.latestFinalizedEpoch = latestFinalizedEpoch;
    this.bestRoot = bestRoot;
    this.bestSlot = bestSlot;
  }

  @JsonProperty("network_id")
  public short networkId() {
    return networkId;
  }

  @JsonProperty("chain_id")
  public short chainId() {
    return chainId;
  }

  @JsonProperty("latest_finalized_root")
  public byte[] latestFinalizedRoot() {
    return latestFinalizedRoot;
  }

  @JsonProperty("latest_finalized_epoch")
  public BigInteger latestFinalizedEpoch() {
    return latestFinalizedEpoch;
  }

  @JsonProperty("best_root")
  public byte[] bestRoot() {
    return bestRoot;
  }

  @JsonProperty("best_slot")
  public BigInteger bestSlot() {
    return bestSlot;
  }
}
