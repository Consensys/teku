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

package tech.pegasys.artemis.datastructures.networking.hobbits.rpc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigInteger;

public final class RequestBlocksMessage {

  private final byte[] startRoot;
  private final BigInteger startSlot;
  private final BigInteger max;
  private final BigInteger skip;
  private final boolean direction;

  @JsonCreator
  public RequestBlocksMessage(
      @JsonProperty("start_root") byte[] startRoot,
      @JsonProperty("start_slot") BigInteger startSlot,
      @JsonProperty("max") BigInteger max,
      @JsonProperty("skip") BigInteger skip,
      @JsonProperty("direction") short direction) {
    this.startRoot = startRoot;
    this.startSlot = startSlot;
    this.max = max;
    this.skip = skip;
    this.direction = direction == 1;
  }

  @JsonProperty("start_root")
  public byte[] startRoot() {
    return startRoot;
  }

  @JsonProperty("start_slot")
  public BigInteger startSlot() {
    return startSlot;
  }

  @JsonProperty("max")
  public BigInteger max() {
    return max;
  }

  @JsonProperty("skip")
  public BigInteger skip() {
    return skip;
  }

  public boolean direction() {
    return direction;
  }

  @JsonProperty("direction")
  public short directionAsShort() {
    return direction ? (short) 1 : 0;
  }
}
