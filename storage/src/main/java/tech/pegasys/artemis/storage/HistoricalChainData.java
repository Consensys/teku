/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.artemis.storage;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.time.Duration;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.storage.events.GetBlockByBlockRootRequest;
import tech.pegasys.artemis.storage.events.GetBlockByBlockRootResponse;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotResponse;
import tech.pegasys.artemis.storage.events.GetFinalizedStateAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetFinalizedStateAtSlotResponse;
import tech.pegasys.artemis.storage.events.GetFinalizedStateByBlockRootRequest;
import tech.pegasys.artemis.storage.events.GetFinalizedStateByBlockRootResponse;
import tech.pegasys.artemis.storage.events.GetLatestFinalizedBlockAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetLatestFinalizedBlockAtSlotResponse;
import tech.pegasys.artemis.util.async.AsyncEventTracker;
import tech.pegasys.artemis.util.async.SafeFuture;

public class HistoricalChainData {
  private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(10);
  private final AsyncEventTracker<UnsignedLong, Optional<SignedBeaconBlock>> blockAtSlotRequests;
  private final AsyncEventTracker<UnsignedLong, Optional<SignedBeaconBlock>>
      latestBlockAtSlotRequests;
  private final AsyncEventTracker<UnsignedLong, Optional<BeaconState>> stateAtSlotRequests;
  private final AsyncEventTracker<Bytes32, Optional<BeaconState>> stateByBlockRootRequests;
  private final AsyncEventTracker<Bytes32, Optional<SignedBeaconBlock>> blockByBlockRootRequests;

  public HistoricalChainData(final EventBus eventBus) {
    this.blockAtSlotRequests = new AsyncEventTracker<>(eventBus);
    this.latestBlockAtSlotRequests = new AsyncEventTracker<>(eventBus);
    this.stateAtSlotRequests = new AsyncEventTracker<>(eventBus);
    this.stateByBlockRootRequests = new AsyncEventTracker<>(eventBus);
    this.blockByBlockRootRequests = new AsyncEventTracker<>(eventBus);
    eventBus.register(this);
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlot(final UnsignedLong slot) {
    return blockAtSlotRequests.sendRequest(
        slot, new GetFinalizedBlockAtSlotRequest(slot), QUERY_TIMEOUT);
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(
      final UnsignedLong slot) {
    return latestBlockAtSlotRequests.sendRequest(
        slot, new GetLatestFinalizedBlockAtSlotRequest(slot), QUERY_TIMEOUT);
  }

  public SafeFuture<Optional<BeaconState>> getFinalizedStateAtSlot(final UnsignedLong slot) {
    return stateAtSlotRequests.sendRequest(
        slot, new GetFinalizedStateAtSlotRequest(slot), QUERY_TIMEOUT);
  }

  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(final Bytes32 blockRoot) {
    return stateByBlockRootRequests.sendRequest(
        blockRoot, new GetFinalizedStateByBlockRootRequest(blockRoot), QUERY_TIMEOUT);
  }

  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return blockByBlockRootRequests.sendRequest(
        blockRoot, new GetBlockByBlockRootRequest(blockRoot), QUERY_TIMEOUT);
  }

  @Subscribe
  @AllowConcurrentEvents
  public void onBlockAtSlotResponse(final GetFinalizedBlockAtSlotResponse response) {
    blockAtSlotRequests.onResponse(response.getSlot(), response.getBlock());
  }

  @Subscribe
  @AllowConcurrentEvents
  public void onLatestBlockAtSlotResponse(final GetLatestFinalizedBlockAtSlotResponse response) {
    latestBlockAtSlotRequests.onResponse(response.getSlot(), response.getBlock());
  }

  @Subscribe
  @AllowConcurrentEvents
  public void onStateAtSlotResponse(final GetFinalizedStateAtSlotResponse response) {
    stateAtSlotRequests.onResponse(response.getSlot(), response.getState());
  }

  @Subscribe
  @AllowConcurrentEvents
  public void onStateByBlockRootResponse(final GetFinalizedStateByBlockRootResponse response) {
    stateByBlockRootRequests.onResponse(response.getBlockRoot(), response.getState());
  }

  @Subscribe
  @AllowConcurrentEvents
  public void onBlockByBlockRootResponse(final GetBlockByBlockRootResponse response) {
    blockByBlockRootRequests.onResponse(response.getBlockRoot(), response.getBlock());
  }
}
