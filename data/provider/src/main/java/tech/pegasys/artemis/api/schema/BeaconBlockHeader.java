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

package tech.pegasys.artemis.api.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;

public class BeaconBlockHeader {
  public final UnsignedLong slot;
  public final Bytes32 parent_root;
  public final Bytes32 state_root;
  public final Bytes32 body_root;

  @JsonCreator
  public BeaconBlockHeader(
      @JsonProperty("slot") final UnsignedLong slot,
      @JsonProperty("parent_root") final Bytes32 parent_root,
      @JsonProperty("state_root") final Bytes32 state_root,
      @JsonProperty("body_root") final Bytes32 body_root) {
    this.slot = slot;
    this.parent_root = parent_root;
    this.state_root = state_root;
    this.body_root = body_root;
  }

  public BeaconBlockHeader(
      final tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader header) {
    this.slot = header.getSlot();
    this.parent_root = header.getParent_root();
    this.state_root = header.getState_root();
    this.body_root = header.getBody_root();
  }
}
