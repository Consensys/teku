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

package tech.pegasys.teku.datastructures.forkchoice;

import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.primitives.UnsignedLong;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.operations.IndexedAttestation;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.state.CheckpointAndBlock;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.util.config.Constants;

public class TestStoreFactory {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();

  public ReadOnlyStore createGenesisStore() {
    final BeaconState genesisStore = createRandomGenesisState();
    return getForkChoiceStore(StoreImpl::new, genesisStore);
  }

  public ReadOnlyStore createGenesisStore(final BeaconState genesisState) {
    return getForkChoiceStore(StoreImpl::new, genesisState);
  }

  public MutableStore createMutableGenesisStore() {
    final BeaconState genesisStore = createRandomGenesisState();
    return getForkChoiceStore(MutableStoreImpl::new, genesisStore);
  }

  public MutableStore createEmptyStore() {
    return new MutableStoreImpl(
        UnsignedLong.ZERO,
        UnsignedLong.ZERO,
        null,
        null,
        null,
        new HashMap<>(),
        new HashMap<>(),
        new HashMap<>());
  }

  private <T extends ReadOnlyStore> T getForkChoiceStore(
      StoreConstructor<T> storeConstructor, final BeaconState anchorState) {
    final BeaconBlock anchorBlock = new BeaconBlock(anchorState.hash_tree_root());
    final SignedBeaconBlock signedAnchorBlock =
        new SignedBeaconBlock(anchorBlock, BLSSignature.empty());
    final Bytes32 anchorRoot = anchorBlock.hash_tree_root();
    final UnsignedLong anchorEpoch = BeaconStateUtil.get_current_epoch(anchorState);
    final Checkpoint anchorCheckpoint = new Checkpoint(anchorEpoch, anchorRoot);

    Map<Bytes32, SignedBeaconBlock> blocks = new HashMap<>();
    Map<Bytes32, BeaconState> block_states = new HashMap<>();
    Map<Checkpoint, BeaconState> checkpoint_states = new HashMap<>();

    blocks.put(anchorRoot, signedAnchorBlock);
    block_states.put(anchorRoot, anchorState);
    checkpoint_states.put(anchorCheckpoint, anchorState);

    return storeConstructor.create(
        anchorState
            .getGenesis_time()
            .plus(UnsignedLong.valueOf(SECONDS_PER_SLOT).times(anchorState.getSlot())),
        anchorState.getGenesis_time(),
        anchorCheckpoint,
        anchorCheckpoint,
        anchorCheckpoint,
        blocks,
        block_states,
        checkpoint_states);
  }

  private BeaconState createRandomGenesisState() {
    return dataStructureUtil.randomBeaconState(UnsignedLong.valueOf(Constants.GENESIS_SLOT));
  }

  private static class StoreImpl implements ReadOnlyStore {
    protected UnsignedLong time;
    protected UnsignedLong genesis_time;
    protected Checkpoint justified_checkpoint;
    protected Checkpoint finalized_checkpoint;
    protected Checkpoint best_justified_checkpoint;
    protected Map<Bytes32, SignedBeaconBlock> blocks;
    protected Map<Bytes32, BeaconState> block_states;
    protected Map<Checkpoint, BeaconState> checkpoint_states;

    StoreImpl(
        final UnsignedLong time,
        final UnsignedLong genesis_time,
        final Checkpoint justified_checkpoint,
        final Checkpoint finalized_checkpoint,
        final Checkpoint best_justified_checkpoint,
        final Map<Bytes32, SignedBeaconBlock> blocks,
        final Map<Bytes32, BeaconState> block_states,
        final Map<Checkpoint, BeaconState> checkpoint_states) {
      this.time = time;
      this.genesis_time = genesis_time;
      this.justified_checkpoint = justified_checkpoint;
      this.finalized_checkpoint = finalized_checkpoint;
      this.best_justified_checkpoint = best_justified_checkpoint;
      this.blocks = blocks;
      this.block_states = block_states;
      this.checkpoint_states = checkpoint_states;
    }

    @Override
    public UnsignedLong getTime() {
      return time;
    }

    @Override
    public UnsignedLong getGenesisTime() {
      return genesis_time;
    }

    @Override
    public Checkpoint getJustifiedCheckpoint() {
      return justified_checkpoint;
    }

    @Override
    public Checkpoint getFinalizedCheckpoint() {
      return finalized_checkpoint;
    }

    @Override
    public CheckpointAndBlock getFinalizedCheckpointAndBlock() {
      final SignedBeaconBlock block = getSignedBlock(finalized_checkpoint.getRoot());
      return new CheckpointAndBlock(finalized_checkpoint, block);
    }

    @Override
    public UnsignedLong getLatestFinalizedBlockSlot() {
      return blocks.get(finalized_checkpoint.getRoot()).getSlot();
    }

    @Override
    public SignedBlockAndState getLatestFinalizedBlockAndState() {
      final SignedBeaconBlock block = getSignedBlock(finalized_checkpoint.getRoot());
      final BeaconState state = getBlockState(finalized_checkpoint.getRoot());
      return new SignedBlockAndState(block, state);
    }

    @Override
    public Checkpoint getBestJustifiedCheckpoint() {
      return best_justified_checkpoint;
    }

    @Override
    public BeaconBlock getBlock(final Bytes32 blockRoot) {
      return blocks.get(blockRoot).getMessage();
    }

    @Override
    public SignedBeaconBlock getSignedBlock(final Bytes32 blockRoot) {
      return blocks.get(blockRoot);
    }

    @Override
    public Optional<SignedBlockAndState> getBlockAndState(final Bytes32 blockRoot) {
      final SignedBeaconBlock block = getSignedBlock(blockRoot);
      final BeaconState state = getBlockState(blockRoot);
      if (block == null || state == null) {
        return Optional.empty();
      }
      return Optional.of(new SignedBlockAndState(block, state));
    }

    @Override
    public boolean containsBlock(final Bytes32 blockRoot) {
      return blocks.containsKey(blockRoot);
    }

    @Override
    public Bytes32 getHead() {
      // Just return a block with the highest slot
      return blocks.values().stream()
          .sorted(Comparator.comparing(SignedBeaconBlock::getSlot).reversed())
          .findFirst()
          .map(SignedBeaconBlock::getRoot)
          .orElseThrow();
    }

    @Override
    public Optional<UnsignedLong> getBlockSlot(final Bytes32 blockRoot) {
      return Optional.ofNullable(blocks.get(blockRoot)).map(SignedBeaconBlock::getSlot);
    }

    @Override
    public Optional<Bytes32> getBlockParent(final Bytes32 blockRoot) {
      return Optional.ofNullable(blocks.get(blockRoot)).map(SignedBeaconBlock::getParent_root);
    }

    @Override
    public Set<Bytes32> getBlockRoots() {
      return blocks.keySet();
    }

    @Override
    public BeaconState getBlockState(final Bytes32 blockRoot) {
      return block_states.get(blockRoot);
    }

    @Override
    public BeaconState getCheckpointState(final Checkpoint checkpoint) {
      return checkpoint_states.get(checkpoint);
    }

    @Override
    public boolean containsCheckpointState(final Checkpoint checkpoint) {
      return checkpoint_states.containsKey(checkpoint);
    }
  }

  private static class MutableStoreImpl extends StoreImpl implements MutableStore {

    MutableStoreImpl(
        final UnsignedLong time,
        final UnsignedLong genesis_time,
        final Checkpoint justified_checkpoint,
        final Checkpoint finalized_checkpoint,
        final Checkpoint best_justified_checkpoint,
        final Map<Bytes32, SignedBeaconBlock> blocks,
        final Map<Bytes32, BeaconState> block_states,
        final Map<Checkpoint, BeaconState> checkpoint_states) {
      super(
          time,
          genesis_time,
          justified_checkpoint,
          finalized_checkpoint,
          best_justified_checkpoint,
          blocks,
          block_states,
          checkpoint_states);
    }

    @Override
    public void putCheckpointState(final Checkpoint checkpoint, final BeaconState state) {
      checkpoint_states.put(checkpoint, state);
    }

    @Override
    public void putBlockAndState(final SignedBeaconBlock block, final BeaconState state) {
      blocks.put(block.getRoot(), block);
      block_states.put(block.getRoot(), state);
    }

    @Override
    public void putBlockAndState(final SignedBlockAndState blockAndState) {
      blocks.put(blockAndState.getRoot(), blockAndState.getBlock());
      block_states.put(blockAndState.getRoot(), blockAndState.getState());
    }

    @Override
    public void setTime(final UnsignedLong time) {
      this.time = time;
    }

    @Override
    public void setGenesis_time(final UnsignedLong genesis_time) {
      this.genesis_time = genesis_time;
    }

    @Override
    public void setJustifiedCheckpoint(final Checkpoint justified_checkpoint) {
      this.justified_checkpoint = justified_checkpoint;
    }

    @Override
    public void setFinalizedCheckpoint(final Checkpoint finalized_checkpoint) {
      this.finalized_checkpoint = finalized_checkpoint;
    }

    @Override
    public void setBestJustifiedCheckpoint(final Checkpoint best_justified_checkpoint) {
      this.best_justified_checkpoint = best_justified_checkpoint;
    }

    @Override
    public void updateHead() {
      // No-op
    }

    @Override
    public void processAttestation(final IndexedAttestation attestation) {
      // No-op
    }
  }

  @FunctionalInterface
  private interface StoreConstructor<T extends ReadOnlyStore> {
    T create(
        final UnsignedLong time,
        final UnsignedLong genesis_time,
        final Checkpoint justified_checkpoint,
        final Checkpoint finalized_checkpoint,
        final Checkpoint best_justified_checkpoint,
        final Map<Bytes32, SignedBeaconBlock> blocks,
        final Map<Bytes32, BeaconState> block_states,
        final Map<Checkpoint, BeaconState> checkpoint_states);
  }
}
