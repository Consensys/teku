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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.util.config.Constants.SECONDS_PER_SLOT;

import com.google.common.primitives.UnsignedLong;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.datastructures.util.BeaconStateUtil;

public class StoreBuilder {
  MetricsSystem metricsSystem;
  BlockProvider blockProvider;

  final Map<Bytes32, Bytes32> childToParentRoot = new HashMap<>();
  UnsignedLong time;
  UnsignedLong genesis_time;
  Checkpoint justified_checkpoint;
  Checkpoint finalized_checkpoint;
  Checkpoint best_justified_checkpoint;
  Map<Checkpoint, BeaconState> checkpoint_states;
  SignedBlockAndState latestFinalized;
  Map<UnsignedLong, VoteTracker> votes;

  private StoreBuilder() {}

  public static StoreBuilder create() {
    return new StoreBuilder();
  }

  public static UpdatableStore buildForkChoiceStore(
      final MetricsSystem metricsSystem,
      final BlockProvider blockProvider,
      final BeaconState anchorState) {
    return forkChoiceStoreBuilder(metricsSystem, blockProvider, anchorState).build();
  }

  public static StoreBuilder forkChoiceStoreBuilder(
      final MetricsSystem metricsSystem,
      final BlockProvider blockProvider,
      final BeaconState anchorState) {
    final UnsignedLong time =
        anchorState
            .getGenesis_time()
            .plus(UnsignedLong.valueOf(SECONDS_PER_SLOT).times(anchorState.getSlot()));
    final BeaconBlock anchorBlock = new BeaconBlock(anchorState.hash_tree_root());
    final SignedBeaconBlock signedAnchorBlock =
        new SignedBeaconBlock(anchorBlock, BLSSignature.empty());
    final Bytes32 anchorRoot = anchorBlock.hash_tree_root();
    final UnsignedLong anchorEpoch = BeaconStateUtil.get_current_epoch(anchorState);
    final Checkpoint anchorCheckpoint = new Checkpoint(anchorEpoch, anchorRoot);

    Map<Bytes32, Bytes32> childToParentMap = new HashMap<>();
    Map<Checkpoint, BeaconState> checkpoint_states = new HashMap<>();
    Map<UnsignedLong, VoteTracker> votes = new HashMap<>();

    childToParentMap.put(anchorRoot, anchorBlock.getParent_root());
    checkpoint_states.put(anchorCheckpoint, anchorState);

    return create()
        .metricsSystem(metricsSystem)
        .blockProvider(blockProvider)
        .time(time)
        .genesis_time(anchorState.getGenesis_time())
        .finalized_checkpoint(anchorCheckpoint)
        .justified_checkpoint(anchorCheckpoint)
        .best_justified_checkpoint(anchorCheckpoint)
        .childToParentMap(childToParentMap)
        .checkpoint_states(checkpoint_states)
        .latestFinalized(new SignedBlockAndState(signedAnchorBlock, anchorState))
        .votes(votes);
  }

  public UpdatableStore build() {
    assertValid();
    return new Store(
        metricsSystem,
        blockProvider,
        time,
        genesis_time,
        justified_checkpoint,
        finalized_checkpoint,
        best_justified_checkpoint,
        childToParentRoot,
        checkpoint_states,
        latestFinalized,
        votes,
        StorePruningOptions.createDefault());
  }

  private void assertValid() {
    checkState(metricsSystem != null, "Metrics system must be defined");
    checkState(blockProvider != null, "Block provider must be defined");
    checkState(time != null, "Time must be defined");
    checkState(genesis_time != null, "Genesis time must be defined");
    checkState(justified_checkpoint != null, "Justified checkpoint must be defined");
    checkState(finalized_checkpoint != null, "Finalized checkpoint must be defined");
    checkState(best_justified_checkpoint != null, "Best justified checkpoint must be defined");
    checkState(!childToParentRoot.isEmpty(), "Parent and child block data must be supplied");
    checkState(checkpoint_states != null, "Checkpoint states must be defined");
    checkState(latestFinalized != null, "Latest finalized block state must be defined");
    checkState(votes != null, "Votes must be defined");
  }

  public StoreBuilder metricsSystem(final MetricsSystem metricsSystem) {
    checkNotNull(metricsSystem);
    this.metricsSystem = metricsSystem;
    return this;
  }

  public StoreBuilder blockProvider(final BlockProvider blockProvider) {
    checkNotNull(blockProvider);
    this.blockProvider = blockProvider;
    return this;
  }

  public StoreBuilder time(final UnsignedLong time) {
    checkNotNull(time);
    this.time = time;
    return this;
  }

  public StoreBuilder genesis_time(final UnsignedLong genesis_time) {
    checkNotNull(genesis_time);
    this.genesis_time = genesis_time;
    return this;
  }

  public StoreBuilder justified_checkpoint(final Checkpoint justified_checkpoint) {
    checkNotNull(justified_checkpoint);
    this.justified_checkpoint = justified_checkpoint;
    return this;
  }

  public StoreBuilder finalized_checkpoint(final Checkpoint finalized_checkpoint) {
    checkNotNull(finalized_checkpoint);
    this.finalized_checkpoint = finalized_checkpoint;
    return this;
  }

  public StoreBuilder best_justified_checkpoint(final Checkpoint best_justified_checkpoint) {
    checkNotNull(best_justified_checkpoint);
    this.best_justified_checkpoint = best_justified_checkpoint;
    return this;
  }

  public StoreBuilder childToParentMap(final Map<Bytes32, Bytes32> childToParent) {
    checkNotNull(childToParent);
    this.childToParentRoot.putAll(childToParent);
    return this;
  }

  public StoreBuilder checkpoint_states(final Map<Checkpoint, BeaconState> checkpoint_states) {
    checkNotNull(checkpoint_states);
    this.checkpoint_states = checkpoint_states;
    return this;
  }

  public StoreBuilder latestFinalized(final SignedBlockAndState latestFinalized) {
    checkNotNull(latestFinalized);
    this.latestFinalized = latestFinalized;
    return this;
  }

  public StoreBuilder votes(final Map<UnsignedLong, VoteTracker> votes) {
    checkNotNull(votes);
    this.votes = votes;
    return this;
  }
}
