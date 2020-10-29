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

package tech.pegasys.teku.datastructures.state;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.datastructures.util.BeaconStateUtil.compute_next_epoch_boundary;

import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

/**
 * Represents an "anchor" - a trusted, finalized (block, state, checkpoint) tuple from which we can
 * sync.
 */
public class AnchorPoint {
  private final Checkpoint checkpoint;
  private final SignedBeaconBlock block;
  private final BeaconState state;
  private final boolean isGenesis;
  private final SignedBlockAndState blockAndState;

  private AnchorPoint(
      final Checkpoint checkpoint, final SignedBeaconBlock block, final BeaconState state) {
    checkArgument(
        block.getStateRoot().equals(state.hash_tree_root()), "Block and state must match");
    checkArgument(checkpoint.getRoot().equals(block.getRoot()), "Checkpoint and block must match");

    this.checkpoint = checkpoint;
    this.block = block;
    this.state = state;
    this.isGenesis = checkpoint.getEpoch().equals(UInt64.valueOf(Constants.GENESIS_EPOCH));
    this.blockAndState = new SignedBlockAndState(block, state);
  }

  public static AnchorPoint create(
      Checkpoint checkpoint, SignedBeaconBlock block, BeaconState state) {
    return new AnchorPoint(checkpoint, block, state);
  }

  public static AnchorPoint create(Checkpoint checkpoint, SignedBlockAndState blockAndState) {
    return new AnchorPoint(checkpoint, blockAndState.getBlock(), blockAndState.getState());
  }

  public static AnchorPoint fromGenesisState(final BeaconState genesisState) {
    checkArgument(
        genesisState.getSlot().equals(UInt64.valueOf(Constants.GENESIS_SLOT)),
        "Invalid genesis state supplied");

    final BeaconBlock genesisBlock = new BeaconBlock(genesisState.hash_tree_root());
    final SignedBeaconBlock signedGenesisBlock =
        new SignedBeaconBlock(genesisBlock, BLSSignature.empty());

    final Bytes32 genesisBlockRoot = genesisBlock.hash_tree_root();
    final UInt64 genesisEpoch = BeaconStateUtil.get_current_epoch(genesisState);
    final Checkpoint genesisCheckpoint = new Checkpoint(genesisEpoch, genesisBlockRoot);

    return new AnchorPoint(genesisCheckpoint, signedGenesisBlock, genesisState);
  }

  public static AnchorPoint fromInitialBlockAndState(final SignedBlockAndState blockAndState) {
    return fromInitialBlockAndState(blockAndState.getBlock(), blockAndState.getState());
  }

  public static AnchorPoint fromInitialBlockAndState(
      final SignedBeaconBlock block, final BeaconState state) {
    checkArgument(
        Objects.equals(block.getStateRoot(), state.hash_tree_root()),
        "State must belong to the given block");

    // Calculate closest epoch boundary to use for the checkpoint
    final UInt64 epoch = compute_next_epoch_boundary(state.getSlot());
    final Checkpoint checkpoint = new Checkpoint(epoch, block.getRoot());

    return new AnchorPoint(checkpoint, block, state);
  }

  public boolean isGenesis() {
    return isGenesis;
  }

  public Checkpoint getCheckpoint() {
    return checkpoint;
  }

  public SignedBeaconBlock getBlock() {
    return block;
  }

  public SignedBlockAndState getBlockAndState() {
    return blockAndState;
  }

  public UInt64 getBlockSlot() {
    return block.getSlot();
  }

  public BeaconState getState() {
    return state;
  }

  public UInt64 getEpoch() {
    return checkpoint.getEpoch();
  }

  public UInt64 getEpochStartSlot() {
    return checkpoint.getEpochStartSlot();
  }

  public Bytes32 getRoot() {
    return block.getRoot();
  }

  public Bytes32 getParentRoot() {
    return block.getParent_root();
  }
}
