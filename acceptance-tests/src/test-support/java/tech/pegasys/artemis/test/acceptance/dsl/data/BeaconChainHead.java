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
  public final UnsignedLong head_slot;
  public final UnsignedLong head_epoch;
  public final Bytes32 head_block_root;
  public final UnsignedLong finalized_slot;
  public final UnsignedLong finalized_epoch;
  public final Bytes32 finalized_block_root;
  public final UnsignedLong justified_slot;
  public final UnsignedLong justified_epoch;
  public final Bytes32 justified_block_root;
  public final UnsignedLong previous_justified_slot;
  public final UnsignedLong previous_justified_epoch;
  public final Bytes32 previous_justified_block_root;

  @JsonCreator
  public BeaconChainHead(
      @JsonProperty("head_slot") UnsignedLong head_slot,
      @JsonProperty("head_epoch") UnsignedLong head_epoch,
      @JsonProperty("head_lockroot") Bytes32 head_block_root,
      @JsonProperty("finalized_slot") UnsignedLong finalized_slot,
      @JsonProperty("finalized_epoch") UnsignedLong finalized_epoch,
      @JsonProperty("finalized_block_root") Bytes32 finalized_block_root,
      @JsonProperty("justified_slot") UnsignedLong justified_slot,
      @JsonProperty("justified_epoch") UnsignedLong justified_epoch,
      @JsonProperty("justified_block_root") Bytes32 justified_block_root,
      @JsonProperty("previous_justified_slot") UnsignedLong previous_justified_slot,
      @JsonProperty("previous_justified_epoch") UnsignedLong previous_justified_epoch,
      @JsonProperty("previous_justified_block_root") Bytes32 previous_justified_block_root) {
    this.head_slot = head_slot;
    this.head_epoch = head_epoch;
    this.head_block_root = head_block_root;

    this.finalized_slot = finalized_slot;
    this.finalized_epoch = finalized_epoch;
    this.finalized_block_root = finalized_block_root;

    this.justified_slot = justified_slot;
    this.justified_epoch = justified_epoch;
    this.justified_block_root = justified_block_root;

    this.previous_justified_slot = previous_justified_slot;
    this.previous_justified_epoch = previous_justified_epoch;
    this.previous_justified_block_root = previous_justified_block_root;
  }
}
