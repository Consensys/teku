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

package tech.pegasys.teku.storage.server.noop;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.datastructures.state.AnchorPoint;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.protoarray.ProtoArraySnapshot;
import tech.pegasys.teku.storage.events.StorageUpdate;
import tech.pegasys.teku.storage.events.WeakSubjectivityState;
import tech.pegasys.teku.storage.events.WeakSubjectivityUpdate;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.store.StoreBuilder;

public class NoOpDatabase implements Database {

  @Override
  public void storeInitialAnchor(final AnchorPoint genesis) {}

  @Override
  public void update(final StorageUpdate event) {}

  @Override
  public void storeFinalizedBlocks(final Collection<SignedBeaconBlock> blocks) {}

  @Override
  public void updateWeakSubjectivityState(WeakSubjectivityUpdate weakSubjectivityUpdate) {}

  @Override
  public Optional<StoreBuilder> createMemoryStore() {
    return Optional.empty();
  }

  @Override
  public WeakSubjectivityState getWeakSubjectivityState() {
    return WeakSubjectivityState.empty();
  }

  @Override
  public Map<UInt64, VoteTracker> getVotes() {
    return Collections.emptyMap();
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedBlockRoot(final Bytes32 blockRoot) {
    return Optional.empty();
  }

  @Override
  public Optional<UInt64> getSlotForFinalizedStateRoot(final Bytes32 stateRoot) {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(final UInt64 slot) {
    return Optional.empty();
  }

  @Override
  public Optional<UInt64> getEarliestAvailableBlockSlot() {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getEarliestAvailableBlock() {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(final UInt64 slot) {
    return Optional.empty();
  }

  @Override
  public Optional<SignedBeaconBlock> getSignedBlock(final Bytes32 root) {
    return Optional.empty();
  }

  @Override
  public Optional<BeaconState> getHotState(final Bytes32 root) {
    return Optional.empty();
  }

  @Override
  public Map<Bytes32, SignedBeaconBlock> getHotBlocks(final Set<Bytes32> blockRoots) {
    return Collections.emptyMap();
  }

  @Override
  public Optional<SignedBeaconBlock> getHotBlock(final Bytes32 blockRoot) {
    return Optional.empty();
  }

  @Override
  public Stream<SignedBeaconBlock> streamFinalizedBlocks(
      final UInt64 startSlot, final UInt64 endSlot) {
    return Stream.empty();
  }

  @Override
  public List<Bytes32> getStateRootsBeforeSlot(final UInt64 slot) {
    return Collections.emptyList();
  }

  @Override
  public void addHotStateRoots(
      final Map<Bytes32, SlotAndBlockRoot> stateRootToSlotAndBlockRootMap) {}

  @Override
  public Optional<SlotAndBlockRoot> getSlotAndBlockRootFromStateRoot(final Bytes32 stateRoot) {
    return Optional.empty();
  }

  @Override
  public void pruneHotStateRoots(final List<Bytes32> stateRoots) {}

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(final UInt64 maxSlot) {
    return Optional.empty();
  }

  @Override
  public Optional<MinGenesisTimeBlockEvent> getMinGenesisTimeBlock() {
    return Optional.empty();
  }

  @Override
  public Stream<DepositsFromBlockEvent> streamDepositsFromBlocks() {
    return Stream.empty();
  }

  @Override
  public Optional<ProtoArraySnapshot> getProtoArraySnapshot() {
    return Optional.empty();
  }

  @Override
  public void addMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {}

  @Override
  public void addDepositsFromBlockEvent(final DepositsFromBlockEvent event) {}

  @Override
  public void putProtoArraySnapshot(final ProtoArraySnapshot protoArray) {}

  @Override
  public void close() {}
}
