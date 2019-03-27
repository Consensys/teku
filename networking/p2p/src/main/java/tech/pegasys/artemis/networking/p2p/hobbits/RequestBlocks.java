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

package tech.pegasys.artemis.networking.p2p.hobbits;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.consensys.cava.bytes.Bytes32;

final class RequestBlocks {

  private final Bytes32 startRoot;
  private final long startSlot;
  private final long max;
  private final long skip;
  private final boolean direction;

  @JsonCreator
  RequestBlocks(
      @JsonProperty("start_root") Bytes32 startRoot,
      @JsonProperty("start_slot") long startSlot,
      @JsonProperty("max") long max,
      @JsonProperty("skip") long skip,
      @JsonProperty("direction") int direction) {
    this.startRoot = startRoot;
    this.startSlot = startSlot;
    this.max = max;
    this.skip = skip;
    this.direction = direction == 1;
  }

  @JsonProperty("start_root")
  public Bytes32 startRoot() {
    return startRoot;
  }

  @JsonProperty("start_slot")
  public long startSlot() {
    return startSlot;
  }

  @JsonProperty("max")
  public long max() {
    return max;
  }

  @JsonProperty("skip")
  public long skip() {
    return skip;
  }

  public boolean direction() {
    return direction;
  }

  @JsonProperty("direction")
  public int directionAsInt() {
    return direction ? 1 : 0;
  }
}
