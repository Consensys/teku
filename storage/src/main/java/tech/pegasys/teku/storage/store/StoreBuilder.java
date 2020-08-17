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

import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.lookup.StateProvider;
import tech.pegasys.teku.core.stategenerator.StateGenerationQueue;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.storage.events.AnchorPoint;

public class StoreBuilder {
  MetricsSystem metricsSystem;
  BlockProvider blockProvider;
  StateProvider stateProvider;
  StateGenerationQueue stateGenerationQueue;

  final Map<Bytes32, Bytes32> childToParentRoot = new HashMap<>();
  UInt64 time;
  UInt64 genesisTime;
  Checkpoint justifiedCheckpoint;
  Checkpoint finalizedCheckpoint;
  Checkpoint bestJustifiedCheckpoint;
  SignedBlockAndState latestFinalized;
  Map<UInt64, VoteTracker> votes;

  private StoreBuilder() {}

  public static StoreBuilder create() {
    return new StoreBuilder();
  }

  public static SafeFuture<UpdatableStore> buildForkChoiceStore(
      final MetricsSystem metricsSystem,
      final BlockProvider blockProvider,
      final StateProvider stateProvider,
      final AnchorPoint anchor) {
    return forkChoiceStoreBuilder(metricsSystem, blockProvider, stateProvider, anchor).build();
  }

  public static StoreBuilder forkChoiceStoreBuilder(
      final MetricsSystem metricsSystem,
      final BlockProvider blockProvider,
      final StateProvider stateProvider,
      final AnchorPoint anchor) {
    final UInt64 genesisTime = anchor.getState().getGenesis_time();
    final UInt64 slot = anchor.getState().getSlot();
    final UInt64 time = genesisTime.plus(slot.times(SECONDS_PER_SLOT));

    Map<Bytes32, Bytes32> childToParentMap = new HashMap<>();
    childToParentMap.put(anchor.getRoot(), anchor.getParentRoot());

    return create()
        .metricsSystem(metricsSystem)
        .blockProvider(blockProvider)
        .stateProvider(stateProvider)
        .time(time)
        .genesisTime(genesisTime)
        .finalizedCheckpoint(anchor.getCheckpoint())
        .justifiedCheckpoint(anchor.getCheckpoint())
        .bestJustifiedCheckpoint(anchor.getCheckpoint())
        .childToParentMap(childToParentMap)
        .latestFinalized(anchor.toSignedBlockAndState())
        .votes(new HashMap<>());
  }

  public SafeFuture<UpdatableStore> build() {
    createDefaults();
    assertValid();

    return Store.create(
        metricsSystem,
        blockProvider,
        stateGenerationQueue,
        time,
        genesisTime,
        justifiedCheckpoint,
        finalizedCheckpoint,
        bestJustifiedCheckpoint,
        childToParentRoot,
        latestFinalized,
        votes,
        StorePruningOptions.createDefault());
  }

  private void createDefaults() {
    if (stateGenerationQueue == null) {
      stateGenerationQueue = StateGenerationQueue.create(stateProvider, metricsSystem);
    }
  }

  private void assertValid() {
    checkState(metricsSystem != null, "Metrics system must be defined");
    checkState(blockProvider != null, "Block provider must be defined");
    checkState(stateGenerationQueue != null, "State generation queue must be defined");
    checkState(time != null, "Time must be defined");
    checkState(genesisTime != null, "Genesis time must be defined");
    checkState(justifiedCheckpoint != null, "Justified checkpoint must be defined");
    checkState(finalizedCheckpoint != null, "Finalized checkpoint must be defined");
    checkState(bestJustifiedCheckpoint != null, "Best justified checkpoint must be defined");
    checkState(!childToParentRoot.isEmpty(), "Parent and child block data must be supplied");
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

  public StoreBuilder stateProvider(final StateProvider stateProvider) {
    checkNotNull(stateProvider);
    this.stateProvider = stateProvider;
    return this;
  }

  public StoreBuilder stateGenerationQueue(final StateGenerationQueue stateGenerationQueue) {
    checkNotNull(stateGenerationQueue);
    this.stateGenerationQueue = stateGenerationQueue;
    return this;
  }

  public StoreBuilder time(final UInt64 time) {
    checkNotNull(time);
    this.time = time;
    return this;
  }

  public StoreBuilder genesisTime(final UInt64 genesisTime) {
    checkNotNull(genesisTime);
    this.genesisTime = genesisTime;
    return this;
  }

  public StoreBuilder justifiedCheckpoint(final Checkpoint justifiedCheckpoint) {
    checkNotNull(justifiedCheckpoint);
    this.justifiedCheckpoint = justifiedCheckpoint;
    return this;
  }

  public StoreBuilder finalizedCheckpoint(final Checkpoint finalizedCheckpoint) {
    checkNotNull(finalizedCheckpoint);
    this.finalizedCheckpoint = finalizedCheckpoint;
    return this;
  }

  public StoreBuilder bestJustifiedCheckpoint(final Checkpoint bestJustifiedCheckpoint) {
    checkNotNull(bestJustifiedCheckpoint);
    this.bestJustifiedCheckpoint = bestJustifiedCheckpoint;
    return this;
  }

  public StoreBuilder childToParentMap(final Map<Bytes32, Bytes32> childToParent) {
    checkNotNull(childToParent);
    this.childToParentRoot.putAll(childToParent);
    return this;
  }

  public StoreBuilder latestFinalized(final SignedBlockAndState latestFinalized) {
    checkNotNull(latestFinalized);
    this.latestFinalized = latestFinalized;
    return this;
  }

  public StoreBuilder votes(final Map<UInt64, VoteTracker> votes) {
    checkNotNull(votes);
    this.votes = votes;
    return this;
  }
}
