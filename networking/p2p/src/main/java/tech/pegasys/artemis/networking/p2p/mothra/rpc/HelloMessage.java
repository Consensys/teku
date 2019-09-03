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

package tech.pegasys.artemis.networking.p2p.mothra.rpc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigInteger;
import java.nio.ByteBuffer;

public final class HelloMessage {

  private final byte[] forkVersion;
  private final byte[] finalizedRoot;
  private final BigInteger finalizedEpoch;
  private final byte[] headRoot;
  private final BigInteger headSlot;

  @JsonCreator
  public HelloMessage(
      @JsonProperty("fork_version") int forkVersion,
      @JsonProperty("finalized_root") byte[] finalizedRoot,
      @JsonProperty("finalized_epoch") BigInteger finalizedEpoch,
      @JsonProperty("head_root") byte[] headRoot,
      @JsonProperty("head_slot") BigInteger headSlot) {
    this.forkVersion = ByteBuffer.allocate(4).putInt(forkVersion).array();
    this.finalizedRoot = finalizedRoot;
    this.finalizedEpoch = finalizedEpoch;
    this.headRoot = headRoot;
    this.headSlot = headSlot;
  }

  @JsonProperty("fork_version")
  public byte[] forkVersion() {
    return forkVersion;
  }

  @JsonProperty("finalized_root")
  public byte[] finalizedRoot() {
    return finalizedRoot;
  }

  @JsonProperty("finalized_epoch")
  public BigInteger finalizedEpoch() {
    return finalizedEpoch;
  }

  @JsonProperty("head_root")
  public byte[] headRoot() {
    return headRoot;
  }

  @JsonProperty("head_slot")
  public BigInteger headSlot() {
    return headSlot;
  }
}
