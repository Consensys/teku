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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotRequest;
import tech.pegasys.artemis.storage.events.GetFinalizedBlockAtSlotResponse;
import tech.pegasys.artemis.util.async.AsyncEventTracker;

public class HistoricalChainData {
  private final AsyncEventTracker<UnsignedLong, Optional<BeaconBlock>> eventTracker;

  public HistoricalChainData(final EventBus eventBus) {
    this.eventTracker = new AsyncEventTracker<>(eventBus);
    eventBus.register(this);
  }

  public CompletableFuture<Optional<BeaconBlock>> getFinalizedBlockAtSlot(final UnsignedLong slot) {
    return eventTracker.sendRequest(slot, new GetFinalizedBlockAtSlotRequest(slot));
  }

  @Subscribe
  @AllowConcurrentEvents
  public void onResponse(final GetFinalizedBlockAtSlotResponse response) {
    eventTracker.onResponse(response.getSlot(), response.getBlock());
  }
}
