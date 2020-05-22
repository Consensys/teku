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

package tech.pegasys.teku.storage.server;

import com.google.common.eventbus.EventBus;
import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.storage.api.Eth1DepositChannel;
import tech.pegasys.teku.util.async.SafeFuture;

public class DepositStorage implements Eth1DepositChannel, Eth1EventsChannel {
  private final EventBus eventBus;
  private final Database database;

  private DepositStorage(final EventBus eventBus, final Database database) {
    this.eventBus = eventBus;
    this.database = database;
  }

  public static DepositStorage create(final EventBus eventBus, final Database database) {
    return new DepositStorage(eventBus, database);
  }

  public void start() {
    eventBus.register(this);
  }

  public void stop() {
    eventBus.unregister(this);
  }

  @Override
  public SafeFuture<Stream<DepositsFromBlockEvent>> streamDepositFromBlockEvents() {
    return SafeFuture.completedFuture(database.streamDepositsFromBlocks());
  }

  @Override
  public SafeFuture<Optional<MinGenesisTimeBlockEvent>> getMinGenesisTimeBlockEvent() {
    return SafeFuture.completedFuture(database.getMinGenesisTimeBlock());
  }

  @Override
  public void onDepositsFromBlock(final DepositsFromBlockEvent event) {
    database.addDepositsFromBlockEvent(event);
  }

  @Override
  public void onMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    database.addMinGenesisTimeBlock(event);
  }
}
