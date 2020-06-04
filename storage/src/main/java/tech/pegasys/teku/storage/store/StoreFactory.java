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

import com.google.common.collect.Sets;
import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.StateGenerator;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;

public abstract class StoreFactory {
  private static final Logger LOG = LogManager.getLogger();

  public static UpdatableStore getForkChoiceStore(final BeaconState anchorState) {
    final BeaconBlock anchorBlock = new BeaconBlock(anchorState.hash_tree_root());
    final Bytes32 anchorRoot = anchorBlock.hash_tree_root();
    final UnsignedLong anchorEpoch = BeaconStateUtil.get_current_epoch(anchorState);
    final Checkpoint anchorCheckpoint = new Checkpoint(anchorEpoch, anchorRoot);
    Map<Bytes32, SignedBeaconBlock> blocks = new HashMap<>();
    Map<Bytes32, BeaconState> block_states = new HashMap<>();
    Map<Checkpoint, BeaconState> checkpoint_states = new HashMap<>();
    Map<UnsignedLong, VoteTracker> votes = new HashMap<>();

    blocks.put(anchorRoot, new SignedBeaconBlock(anchorBlock, BLSSignature.empty()));
    block_states.put(anchorRoot, anchorState);
    checkpoint_states.put(anchorCheckpoint, anchorState);

    return create(
        anchorState
            .getGenesis_time()
            .plus(UnsignedLong.valueOf(SECONDS_PER_SLOT).times(anchorState.getSlot())),
        anchorState.getGenesis_time(),
        anchorCheckpoint,
        anchorCheckpoint,
        anchorCheckpoint,
        blocks,
        block_states,
        checkpoint_states,
        votes);
  }

  public static UpdatableStore createByRegeneratingHotStates(
      final UnsignedLong time,
      final UnsignedLong genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final Map<Checkpoint, BeaconState> checkpoint_states,
      final BeaconState finalizedState,
      final Map<UnsignedLong, VoteTracker> votes) {

    final StateGenerator stateGenerator = new StateGenerator();
    final Map<Bytes32, BeaconState> blockStates =
        stateGenerator.produceStatesForBlocks(
            finalized_checkpoint.getRoot(), finalizedState, blocks.values());

    // If we couldn't regenerate states, log a warning
    if (blockStates.size() < blocks.size()) {
      LOG.warn("Unable to regenerate some hot states from hot blocks");

      // Drop any blocks for which a state couldn't be generated
      new HashSet<>(Sets.difference(blocks.keySet(), blockStates.keySet())).forEach(blocks::remove);
    }

    return create(
        time,
        genesis_time,
        justified_checkpoint,
        finalized_checkpoint,
        best_justified_checkpoint,
        blocks,
        blockStates,
        checkpoint_states,
        votes);
  }

  public static UpdatableStore create(
      final UnsignedLong time,
      final UnsignedLong genesis_time,
      final Checkpoint justified_checkpoint,
      final Checkpoint finalized_checkpoint,
      final Checkpoint best_justified_checkpoint,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final Map<Bytes32, BeaconState> block_states,
      final Map<Checkpoint, BeaconState> checkpoint_states,
      final Map<UnsignedLong, VoteTracker> votes) {
    return new Store(
        time,
        genesis_time,
        justified_checkpoint,
        finalized_checkpoint,
        best_justified_checkpoint,
        blocks,
        block_states,
        checkpoint_states,
        votes);
  }
}
