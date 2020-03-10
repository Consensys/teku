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
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;

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

  public BeaconChainHead(
      UnsignedLong head_slot,
      UnsignedLong head_epoch,
      Bytes32 head_block_root,
      UnsignedLong finalized_slot,
      UnsignedLong finalized_epoch,
      Bytes32 finalized_block_root,
      UnsignedLong justified_slot,
      UnsignedLong justified_epoch,
      Bytes32 justified_block_root,
      UnsignedLong previous_justified_slot,
      UnsignedLong previous_justified_epoch,
      Bytes32 previous_justified_block_root) {
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

  public BeaconChainHead(final BeaconState beaconState) {
    final Checkpoint latestBlockHeader = beaconState.getCurrent_justified_checkpoint();
    this.head_slot = latestBlockHeader.getEpochSlot();
    this.head_epoch = latestBlockHeader.getEpoch();
    this.head_block_root = latestBlockHeader.hash_tree_root();

    final Checkpoint finalizedCheckpoint = beaconState.getFinalized_checkpoint();
    this.finalized_slot = finalizedCheckpoint.getEpochSlot();
    this.finalized_epoch = finalizedCheckpoint.getEpoch();
    this.finalized_block_root = finalizedCheckpoint.hash_tree_root();

    final Checkpoint currentJustifiedCheckpoint = beaconState.getCurrent_justified_checkpoint();
    this.justified_slot = currentJustifiedCheckpoint.getEpochSlot();
    this.justified_epoch = currentJustifiedCheckpoint.getEpoch();
    this.justified_block_root = currentJustifiedCheckpoint.hash_tree_root();

    final Checkpoint previousJustifiedCheckpoint = beaconState.getPrevious_justified_checkpoint();
    this.previous_justified_slot = previousJustifiedCheckpoint.getEpochSlot();
    this.previous_justified_epoch = previousJustifiedCheckpoint.getEpoch();
    this.previous_justified_block_root = previousJustifiedCheckpoint.hash_tree_root();
  }
}
