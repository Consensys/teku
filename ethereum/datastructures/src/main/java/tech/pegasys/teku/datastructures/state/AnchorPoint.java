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
public class AnchorPoint extends StateAndBlockSummary {
  private final Checkpoint checkpoint;
  private final BeaconBlockHeader blockHeader;
  private final Optional<SignedBeaconBlock> block;
  private final boolean isGenesis;

  private AnchorPoint(
      final Checkpoint checkpoint,
      final BeaconState state,
      final BeaconBlockHeader header,
      final Optional<SignedBeaconBlock> block) {
    super(block.map(BeaconBlockSummary.class::cast).orElse(header), state);
    checkArgument(
        checkpoint.getRoot().equals(header.hashTreeRoot()), "Checkpoint and block must match");

    this.blockHeader = header;
    this.checkpoint = checkpoint;
    this.block = block;
    this.isGenesis = checkpoint.getEpoch().equals(UInt64.valueOf(Constants.GENESIS_EPOCH));
  }

  public static AnchorPoint create(
      Checkpoint checkpoint, BeaconState state, Optional<SignedBeaconBlock> block) {
    final BeaconBlockHeader header =
        block.map(BeaconBlockHeader::fromBlock).orElseGet(() -> BeaconBlockHeader.fromState(state));
    return new AnchorPoint(checkpoint, state, header, block);
  }

  public static AnchorPoint create(
      Checkpoint checkpoint, SignedBeaconBlock block, BeaconState state) {
    return new AnchorPoint(
        checkpoint, state, BeaconBlockHeader.fromBlock(block), Optional.of(block));
  }

  public static AnchorPoint create(Checkpoint checkpoint, SignedBlockAndState blockAndState) {
    return new AnchorPoint(
        checkpoint,
        blockAndState.getState(),
        BeaconBlockHeader.fromBlock(blockAndState.getBlock()),
        Optional.of(blockAndState.getBlock()));
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

    return new AnchorPoint(
        genesisCheckpoint,
        genesisState,
        BeaconBlockHeader.fromBlock(genesisBlock),
        Optional.of(signedGenesisBlock));
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

    return new AnchorPoint(
        checkpoint, state, BeaconBlockHeader.fromBlock(block), Optional.of(block));
  }

  public boolean isGenesis() {
    return isGenesis;
  }

  public Checkpoint getCheckpoint() {
    return checkpoint;
  }

  public BeaconBlockHeader getBlockHeader() {
    return blockHeader;
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

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    final AnchorPoint that = (AnchorPoint) o;
    return Objects.equals(checkpoint, that.checkpoint)
        && Objects.equals(blockHeader, that.blockHeader)
        && Objects.equals(block, that.block);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), checkpoint, blockHeader, block);
  }
}
