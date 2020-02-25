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
import tech.pegasys.artemis.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotResponse;
import tech.pegasys.artemis.storage.events.GetLatestFinalizedBlockAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetLatestFinalizedBlockAtSlotResponse;
import tech.pegasys.artemis.util.async.AsyncEventTracker;
import tech.pegasys.artemis.util.async.SafeFuture;

public class HistoricalChainData {
  private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(10);
  private final AsyncEventTracker<UnsignedLong, Optional<SignedBeaconBlock>> blockAtSlotRequests;
  private final AsyncEventTracker<UnsignedLong, Optional<SignedBeaconBlock>>
      latestBlockAtSlotRequests;

  public HistoricalChainData(final EventBus eventBus) {
    this.blockAtSlotRequests = new AsyncEventTracker<>(eventBus);
    this.latestBlockAtSlotRequests = new AsyncEventTracker<>(eventBus);
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
}
