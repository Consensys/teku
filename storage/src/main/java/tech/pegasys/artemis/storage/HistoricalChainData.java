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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.storage.events.GetBlockBySlotRequest;
import tech.pegasys.artemis.storage.events.GetBlockBySlotResponse;

public class HistoricalChainData {
  private final ConcurrentMap<UnsignedLong, CompletableFuture<Optional<BeaconBlock>>>
      blockBySlotRequests = new ConcurrentHashMap<>();
  private final EventBus eventBus;

  public HistoricalChainData(final EventBus eventBus) {
    this.eventBus = eventBus;
    eventBus.register(this);
  }

  public CompletableFuture<Optional<BeaconBlock>> getBlockBySlot(final UnsignedLong slot) {
    final CompletableFuture<Optional<BeaconBlock>> future =
        blockBySlotRequests.computeIfAbsent(slot, key -> new CompletableFuture<>());
    eventBus.post(new GetBlockBySlotRequest(slot));
    return future;
  }

  @Subscribe
  public void onResponse(final GetBlockBySlotResponse response) {
    final CompletableFuture<Optional<BeaconBlock>> future =
        blockBySlotRequests.remove(response.getRoot());
    if (future != null) {
      future.complete(response.getBlock());
    }
  }
}
