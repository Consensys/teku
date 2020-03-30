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

package tech.pegasys.artemis.storage;

import com.google.common.eventbus.EventBus;

public class MemoryOnlyRecentChainData extends RecentChainData {

  public MemoryOnlyRecentChainData(final EventBus eventBus) {
    super(new StubStorageUpdateChannel(), eventBus);
    eventBus.register(this);
  }

  public static RecentChainData create(final EventBus eventBus) {
    return new MemoryOnlyRecentChainData(eventBus);
  }

  public static RecentChainData createWithStore(final EventBus eventBus, final Store store) {
    MemoryOnlyRecentChainData client = new MemoryOnlyRecentChainData(eventBus);
    client.setStore(store);
    return client;
  }
}
