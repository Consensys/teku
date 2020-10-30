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
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.util.config.Constants;

/**
 * Represents an "anchor" - a trusted, finalized (block, state, checkpoint) tuple from which we can
 * sync.
 */
public class AnchorPoint {
  private final Checkpoint checkpoint;
  private final BeaconState state;
  private final BeaconBlockHeader blockHeader;
  private final Optional<SignedBeaconBlock> block;
  private final boolean isGenesis;

  private AnchorPoint(
      final Checkpoint checkpoint,
      final BeaconState state,
      final Optional<SignedBeaconBlock> block) {
    this.blockHeader =
        block.map(BeaconBlockHeader::fromBlock).orElseGet(() -> BeaconBlockHeader.fromState(state));
    checkArgument(
        blockHeader.getStateRoot().equals(state.hash_tree_root()), "Block and state must match");
    checkArgument(
        checkpoint.getRoot().equals(blockHeader.hashTreeRoot()), "Checkpoint and block must match");

    this.checkpoint = checkpoint;
    this.block = block;
    this.state = state;
    this.isGenesis = checkpoint.getEpoch().equals(UInt64.valueOf(Constants.GENESIS_EPOCH));
  }

  public static AnchorPoint create(
      Checkpoint checkpoint, SignedBeaconBlock block, BeaconState state) {
    return new AnchorPoint(checkpoint, state, Optional.of(block));
  }

  public static AnchorPoint create(Checkpoint checkpoint, SignedBlockAndState blockAndState) {
    return new AnchorPoint(
        checkpoint, blockAndState.getState(), Optional.of(blockAndState.getBlock()));
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

    return new AnchorPoint(genesisCheckpoint, genesisState, Optional.of(signedGenesisBlock));
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

    return new AnchorPoint(checkpoint, state, Optional.of(block));
  }

  public boolean isGenesis() {
    return isGenesis;
  }

  public Checkpoint getCheckpoint() {
    return checkpoint;
  }

  public BeaconState getState() {
    return state;
  }

  public BeaconBlockHeader getBlockHeader() {
    return blockHeader;
  }

  public Optional<SignedBeaconBlock> getBlock() {
    return block;
  }

  public StateAndBlockSummary getStateAndBlockSummary() {
    final BeaconBlockSummary blockSummary =
        block.map(BeaconBlockSummary.class::cast).orElse(blockHeader);
    return new StateAndBlockSummary(blockSummary, state);
  }

  public UInt64 getBlockSlot() {
    return blockHeader.getSlot();
  }

  public UInt64 getEpoch() {
    return checkpoint.getEpoch();
  }

  public UInt64 getEpochStartSlot() {
    return checkpoint.getEpochStartSlot();
  }

  public Bytes32 getRoot() {
    return blockHeader.hashTreeRoot();
  }

  public Bytes32 getParentRoot() {
    return blockHeader.getParentRoot();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final AnchorPoint that = (AnchorPoint) o;
    return isGenesis == that.isGenesis
        && Objects.equals(checkpoint, that.checkpoint)
        && Objects.equals(block, that.block)
        && Objects.equals(state, that.state)
        && Objects.equals(blockAndState, that.blockAndState);
  }

  @Override
  public int hashCode() {
    return Objects.hash(checkpoint, block, state, isGenesis, blockAndState);
  }
}
