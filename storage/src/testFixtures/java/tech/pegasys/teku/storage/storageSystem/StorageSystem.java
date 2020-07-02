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

package tech.pegasys.teku.storage.storageSystem;

import com.google.common.eventbus.EventBus;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.pow.api.TrackingEth1EventsChannel;
import tech.pegasys.teku.storage.api.TrackingReorgEventChannel;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.server.DepositStorage;
import tech.pegasys.teku.storage.server.ProtoArrayStorage;
import tech.pegasys.teku.util.config.StateStorageMode;

public interface StorageSystem extends AutoCloseable {

  DepositStorage createDepositStorage(final boolean eth1DepositsFromStorageEnabled);

  ProtoArrayStorage createProtoArrayStorage();

  Database getDatabase();

  StorageSystem restarted(StateStorageMode storageMode);

  StorageSystem restarted();

  RecentChainData recentChainData();

  CombinedChainDataClient combinedChainDataClient();

  EventBus eventBus();

  TrackingReorgEventChannel reorgEventChannel();

  TrackingEth1EventsChannel eth1EventsChannel();

  ChainBuilder chainBuilder();

  ChainUpdater chainUpdater();

  interface RestartedStorageSupplier {
    StorageSystem restart(final StateStorageMode storageMode);
  }
}
