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

package tech.pegasys.teku.api.schema;

import static tech.pegasys.teku.api.schema.SchemaConstants.DESCRIPTION_BYTES32;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_epoch_at_slot;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BeaconChainHead {
  @Schema(type = "string", format = "uint64")
  public final UInt64 head_slot;

  @Schema(type = "string", format = "uint64")
  public final UInt64 head_epoch;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 head_block_root;

  @Schema(type = "string", format = "uint64")
  public final UInt64 finalized_slot;

  @Schema(type = "string", format = "uint64")
  public final UInt64 finalized_epoch;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 finalized_block_root;

  @Schema(type = "string", format = "uint64")
  public final UInt64 justified_slot;

  @Schema(type = "string", format = "uint64")
  public final UInt64 justified_epoch;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 justified_block_root;

  @Schema(type = "string", format = "uint64")
  public final UInt64 previous_justified_slot;

  @Schema(type = "string", format = "uint64")
  public final UInt64 previous_justified_epoch;

  @Schema(type = "string", format = "byte", description = DESCRIPTION_BYTES32)
  public final Bytes32 previous_justified_block_root;

  @JsonCreator
  public BeaconChainHead(
      @JsonProperty("head_slot") final UInt64 head_slot,
      @JsonProperty("head_epoch") final UInt64 head_epoch,
      @JsonProperty("head_block_root") final Bytes32 head_block_root,
      @JsonProperty("finalized_slot") final UInt64 finalized_slot,
      @JsonProperty("finalized_epoch") final UInt64 finalized_epoch,
      @JsonProperty("finalized_block_root") final Bytes32 finalized_block_root,
      @JsonProperty("justified_slot") final UInt64 justified_slot,
      @JsonProperty("justified_epoch") final UInt64 justified_epoch,
      @JsonProperty("justified_block_root") final Bytes32 justified_block_root,
      @JsonProperty("previous_justified_slot") final UInt64 previous_justified_slot,
      @JsonProperty("previous_justified_epoch") final UInt64 previous_justified_epoch,
      @JsonProperty("previous_justified_block_root") final Bytes32 previous_justified_block_root) {
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

  public BeaconChainHead(final StateAndBlockSummary beaconBlockAndState) {
    final BeaconState beaconState = beaconBlockAndState.getState();

    this.head_slot = beaconBlockAndState.getSlot();
    this.head_epoch = compute_epoch_at_slot(this.head_slot);
    this.head_block_root = beaconBlockAndState.getRoot();

    final Checkpoint finalizedCheckpoint = beaconState.getFinalized_checkpoint();
    this.finalized_slot = finalizedCheckpoint.getEpochStartSlot();
    this.finalized_epoch = finalizedCheckpoint.getEpoch();
    this.finalized_block_root = finalizedCheckpoint.getRoot();

    final Checkpoint currentJustifiedCheckpoint = beaconState.getCurrent_justified_checkpoint();
    this.justified_slot = currentJustifiedCheckpoint.getEpochStartSlot();
    this.justified_epoch = currentJustifiedCheckpoint.getEpoch();
    this.justified_block_root = currentJustifiedCheckpoint.getRoot();

    final Checkpoint previousJustifiedCheckpoint = beaconState.getPrevious_justified_checkpoint();
    this.previous_justified_slot = previousJustifiedCheckpoint.getEpochStartSlot();
    this.previous_justified_epoch = previousJustifiedCheckpoint.getEpoch();
    this.previous_justified_block_root = previousJustifiedCheckpoint.getRoot();
  }
}
