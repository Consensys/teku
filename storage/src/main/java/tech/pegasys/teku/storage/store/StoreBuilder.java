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

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.core.lookup.BlockProvider;
import tech.pegasys.teku.core.lookup.StateAndBlockProvider;
import tech.pegasys.teku.datastructures.blocks.CheckpointEpochs;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.protoarray.ProtoArray;
import tech.pegasys.teku.protoarray.ProtoArrayBuilder;
import tech.pegasys.teku.protoarray.StoredBlockMetadata;

public class StoreBuilder {
  AsyncRunner asyncRunner;
  MetricsSystem metricsSystem;
  BlockProvider blockProvider;
  StateAndBlockProvider stateAndBlockProvider;
  StoreConfig storeConfig = StoreConfig.createDefault();

  final Map<Bytes32, StoredBlockMetadata> blockInfoByRoot = new HashMap<>();
  Optional<Checkpoint> anchor = Optional.empty();
  UInt64 time;
  UInt64 genesisTime;
  AnchorPoint latestFinalized;
  Checkpoint justifiedCheckpoint;
  Checkpoint bestJustifiedCheckpoint;
  Map<UInt64, VoteTracker> votes;

  private StoreBuilder() {}

  public static StoreBuilder create() {
    return new StoreBuilder();
  }

  public static StoreBuilder forkChoiceStoreBuilder(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final BlockProvider blockProvider,
      final StateAndBlockProvider stateAndBlockProvider,
      final AnchorPoint anchor) {
    final UInt64 genesisTime = anchor.getState().getGenesis_time();
    final UInt64 slot = anchor.getState().getSlot();
    final UInt64 time = genesisTime.plus(slot.times(SECONDS_PER_SLOT));

    Map<Bytes32, StoredBlockMetadata> blockInfo = new HashMap<>();
    blockInfo.put(
        anchor.getRoot(),
        new StoredBlockMetadata(
            slot,
            anchor.getRoot(),
            anchor.getParentRoot(),
            anchor.getState().hashTreeRoot(),
            Optional.of(
                new CheckpointEpochs(
                    anchor.getCheckpoint().getEpoch(), anchor.getCheckpoint().getEpoch()))));

    return create()
        .asyncRunner(asyncRunner)
        .metricsSystem(metricsSystem)
        .blockProvider(blockProvider)
        .stateProvider(stateAndBlockProvider)
        .anchor(anchor.getCheckpoint())
        .time(time)
        .genesisTime(genesisTime)
        .latestFinalized(anchor)
        .justifiedCheckpoint(anchor.getCheckpoint())
        .bestJustifiedCheckpoint(anchor.getCheckpoint())
        .blockInformation(blockInfo)
        .votes(new HashMap<>());
  }

  public UpdatableStore build() {
    assertValid();

    return Store.create(
        asyncRunner,
        metricsSystem,
        blockProvider,
        stateAndBlockProvider,
        anchor,
        time,
        genesisTime,
        latestFinalized,
        justifiedCheckpoint,
        bestJustifiedCheckpoint,
        Maps.transformValues(blockInfoByRoot, StoredBlockMetadata::getParentRoot),
        Maps.transformValues(blockInfoByRoot, StoredBlockMetadata::getBlockSlot),
        votes,
        storeConfig);
  }

  public Optional<ProtoArray> buildProtoArray() {
    final List<StoredBlockMetadata> blocks = new ArrayList<>(blockInfoByRoot.values());
    blocks.sort(Comparator.comparing(StoredBlockMetadata::getBlockSlot));
    final ProtoArray protoArray =
        new ProtoArrayBuilder()
            .anchor(anchor)
            .justifiedCheckpoint(justifiedCheckpoint)
            .finalizedCheckpoint(latestFinalized.getCheckpoint())
            .build();
    for (StoredBlockMetadata block : blocks) {
      if (block.getCheckpointEpochs().isEmpty()) {
        // Checkpoint epochs aren't available, migration will be required
        return Optional.empty();
      }
      protoArray.onBlock(
          block.getBlockSlot(),
          block.getBlockRoot(),
          block.getParentRoot(),
          block.getStateRoot(),
          block.getCheckpointEpochs().get().getJustifiedEpoch(),
          block.getCheckpointEpochs().get().getFinalizedEpoch());
    }
    return Optional.of(protoArray);
  }

  private void assertValid() {
    checkState(asyncRunner != null, "Async runner must be defined");
    checkState(metricsSystem != null, "Metrics system must be defined");
    checkState(blockProvider != null, "Block provider must be defined");
    checkState(stateAndBlockProvider != null, "StateAndBlockProvider must be defined");
    checkState(time != null, "Time must be defined");
    checkState(genesisTime != null, "Genesis time must be defined");
    checkState(justifiedCheckpoint != null, "Justified checkpoint must be defined");
    checkState(bestJustifiedCheckpoint != null, "Best justified checkpoint must be defined");
    checkState(latestFinalized != null, "Latest finalized anchor must be defined");
    checkState(votes != null, "Votes must be defined");
    checkState(!blockInfoByRoot.isEmpty(), "Block data must be supplied");
  }

  public StoreBuilder asyncRunner(final AsyncRunner asyncRunner) {
    this.asyncRunner = asyncRunner;
    return this;
  }

  public StoreBuilder metricsSystem(final MetricsSystem metricsSystem) {
    checkNotNull(metricsSystem);
    this.metricsSystem = metricsSystem;
    return this;
  }

  public StoreBuilder storeConfig(final StoreConfig storeConfig) {
    checkNotNull(storeConfig);
    this.storeConfig = storeConfig;
    return this;
  }

  public StoreBuilder blockProvider(final BlockProvider blockProvider) {
    checkNotNull(blockProvider);
    this.blockProvider = blockProvider;
    return this;
  }

  public StoreBuilder stateProvider(final StateAndBlockProvider stateProvider) {
    checkNotNull(stateProvider);
    this.stateAndBlockProvider = stateProvider;
    return this;
  }

  public StoreBuilder anchor(final Checkpoint anchorPoint) {
    checkNotNull(anchorPoint);
    return anchor(Optional.of(anchorPoint));
  }

  public StoreBuilder anchor(final Optional<Checkpoint> anchorPoint) {
    checkNotNull(anchorPoint);
    this.anchor = anchorPoint;
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

  public StoreBuilder latestFinalized(final AnchorPoint latestFinalized) {
    checkNotNull(latestFinalized);
    this.latestFinalized = latestFinalized;
    return this;
  }

  public StoreBuilder bestJustifiedCheckpoint(final Checkpoint bestJustifiedCheckpoint) {
    checkNotNull(bestJustifiedCheckpoint);
    this.bestJustifiedCheckpoint = bestJustifiedCheckpoint;
    return this;
  }

  public StoreBuilder blockInformation(final Map<Bytes32, StoredBlockMetadata> blockInformation) {
    this.blockInfoByRoot.putAll(blockInformation);
    return this;
  }

  public StoreBuilder votes(final Map<UInt64, VoteTracker> votes) {
    checkNotNull(votes);
    this.votes = votes;
    return this;
  }
}
