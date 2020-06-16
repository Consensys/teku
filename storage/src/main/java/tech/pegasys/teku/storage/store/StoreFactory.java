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

package tech.pegasys.teku.storage.store;

import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.StateGenerator;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.BlockTree;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;
import tech.pegasys.teku.storage.store.Store.StateProvider;
import tech.pegasys.teku.util.config.Constants;

public abstract class StoreFactory {
  private static final Logger LOG = LogManager.getLogger();

  public static final int STATE_CACHE_SIZE = Constants.SLOTS_PER_EPOCH * 5;

  public static UpdatableStore getForkChoiceStore(
      final MetricsSystem metricsSystem, final BeaconState anchorState) {
    final BeaconBlock anchorBlock = new BeaconBlock(anchorState.hash_tree_root());
    final Bytes32 anchorRoot = anchorBlock.hash_tree_root();
    final UnsignedLong anchorEpoch = BeaconStateUtil.get_current_epoch(anchorState);
    final Checkpoint anchorCheckpoint = new Checkpoint(anchorEpoch, anchorRoot);
    Map<Bytes32, SignedBeaconBlock> blocks = new HashMap<>();
    Map<Checkpoint, BeaconState> checkpoint_states = new HashMap<>();
    Map<UnsignedLong, VoteTracker> votes = new HashMap<>();

    blocks.put(anchorRoot, new SignedBeaconBlock(anchorBlock, BLSSignature.empty()));
    checkpoint_states.put(anchorCheckpoint, anchorState);

    return create(
        metricsSystem,
        anchorState
            .getGenesis_time()
            .plus(UnsignedLong.valueOf(SECONDS_PER_SLOT).times(anchorState.getSlot())),
        anchorState.getGenesis_time(),
        anchorCheckpoint,
        anchorCheckpoint,
        anchorCheckpoint,
        blocks,
        StateProvider.NOOP,
        checkpoint_states,
        anchorState,
        votes);
  }

  public static UpdatableStore createByRegeneratingHotStates(
      final MetricsSystem metricsSystem,
      final UnsignedLong time,
      final UnsignedLong genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final Map<Checkpoint, BeaconState> checkpoint_states,
      final BeaconState latestFinalizedBlockState,
      final Map<UnsignedLong, VoteTracker> votes) {

    final SignedBeaconBlock finalizedBlock = blocks.get(finalized_checkpoint.getRoot());
    final BlockTree tree =
        BlockTree.builder().rootBlock(finalizedBlock).blocks(blocks.values()).build();
    final StateGenerator stateGenerator = StateGenerator.create(tree, latestFinalizedBlockState);
    final StateProvider stateProvider = stateGenerator::regenerateAllStates;

    if (tree.getBlockCount() < blocks.size()) {
      // This should be an error, but keeping this as a warning now for backwards-compatibility
      // reasons.  Some existing databases may have unpruned fork blocks, and could become unusable
      // if we throw here.  In the future, we should convert this to an error.
      LOG.warn("Ignoring {} non-canonical blocks", blocks.size() - tree.getBlockCount());
    }

    return create(
        metricsSystem,
        time,
        genesis_time,
        justified_checkpoint,
        finalized_checkpoint,
        best_justified_checkpoint,
        blocks,
        stateProvider,
        checkpoint_states,
        latestFinalizedBlockState,
        votes);
  }

  public static UpdatableStore create(
      final MetricsSystem metricsSystem,
      final UnsignedLong time,
      final UnsignedLong genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final StateProvider stateProvider,
      final Map<Checkpoint, BeaconState> checkpoint_states,
      final BeaconState latestFinalizedBlockState,
      final Map<UnsignedLong, VoteTracker> votes) {
    return new Store(
        metricsSystem,
        time,
        genesis_time,
        justified_checkpoint,
        finalized_checkpoint,
        best_justified_checkpoint,
        blocks,
        stateProvider,
        checkpoint_states,
        latestFinalizedBlockState,
        votes,
        STATE_CACHE_SIZE);
  }
}
