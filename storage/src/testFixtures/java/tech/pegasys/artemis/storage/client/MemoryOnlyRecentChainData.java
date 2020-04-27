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

package tech.pegasys.teku.storage.client;

import com.google.common.eventbus.EventBus;
import tech.pegasys.teku.storage.Store;
import tech.pegasys.teku.storage.api.ReorgEventChannel;
import tech.pegasys.teku.storage.api.StubFinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.StubReorgEventChannel;
import tech.pegasys.teku.storage.api.StubStorageUpdateChannel;

public class MemoryOnlyRecentChainData extends RecentChainData {

  public MemoryOnlyRecentChainData(
      final EventBus eventBus, final ReorgEventChannel reorgEventChannel) {
    super(
        new StubStorageUpdateChannel(),
        new StubFinalizedCheckpointChannel(),
        reorgEventChannel,
        eventBus);
    eventBus.register(this);
  }

  public static RecentChainData create(final EventBus eventBus) {
    return create(eventBus, new StubReorgEventChannel());
  }

  public static RecentChainData create(
      final EventBus eventBus, final ReorgEventChannel reorgEventChannel) {
    return new MemoryOnlyRecentChainData(eventBus, reorgEventChannel);
  }

  public static RecentChainData createWithStore(
      final EventBus eventBus, final ReorgEventChannel reorgEventChannel, final Store store) {
    MemoryOnlyRecentChainData client = new MemoryOnlyRecentChainData(eventBus, reorgEventChannel);
    client.setStore(store);
    return client;
  }
}
