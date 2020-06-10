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

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import java.util.stream.Stream;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.schema.ReplayDepositsResult;
import tech.pegasys.teku.util.async.SafeFuture;

public class DepositStorage implements Eth1DepositStorageChannel, Eth1EventsChannel {
  private final Database database;
  private final Eth1EventsChannel eth1EventsChannel;
  private volatile boolean isSyncingFromDatabase = false;
  private final Supplier<SafeFuture<ReplayDepositsResult>> replayResult;

  private DepositStorage(final Eth1EventsChannel eth1EventsChannel, final Database database) {
    this.eth1EventsChannel = eth1EventsChannel;
    this.database = database;
    this.replayResult = Suppliers.memoize(() -> SafeFuture.of(this::replayDeposits));
  }

  public static DepositStorage create(
      final Eth1EventsChannel eth1EventsChannel, final Database database) {
    return new DepositStorage(eth1EventsChannel, database);
  }

  public void start() {}

  public void stop() {}

  @Override
  public SafeFuture<ReplayDepositsResult> replayDepositEvents() {
    return replayResult.get();
  }

  private ReplayDepositsResult replayDeposits() {
    isSyncingFromDatabase = true;
    final DepositSequencer depositSequencer =
        new DepositSequencer(eth1EventsChannel, database.getMinGenesisTimeBlock());
    try (Stream<DepositsFromBlockEvent> eventStream = database.streamDepositsFromBlocks()) {
      eventStream.forEach(depositSequencer::depositEvent);
    }
    depositSequencer.depositsComplete();
    isSyncingFromDatabase = false;
    return depositSequencer.done();
  }

  @Override
  public void onDepositsFromBlock(final DepositsFromBlockEvent event) {
    if (!isSyncingFromDatabase) {
      database.addDepositsFromBlockEvent(event);
    }
  }

  @Override
  public void onMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    if (!isSyncingFromDatabase) {
      database.addMinGenesisTimeBlock(event);
    }
  }

  private static class DepositSequencer {
    private final Eth1EventsChannel eth1EventsChannel;
    private final Optional<MinGenesisTimeBlockEvent> genesis;
    private boolean isGenesisDone;
    private UnsignedLong lastDeposit;

    public DepositSequencer(
        Eth1EventsChannel eventChannel, Optional<MinGenesisTimeBlockEvent> genesis) {
      this.eth1EventsChannel = eventChannel;
      this.genesis = genesis;
      this.isGenesisDone = false;
    }

    public void depositEvent(final DepositsFromBlockEvent event) {
      if (genesis.isPresent()
          && !isGenesisDone
          && genesis.get().getBlockNumber().compareTo(event.getBlockNumber()) < 0) {
        this.eth1EventsChannel.onMinGenesisTimeBlock(genesis.get());
        isGenesisDone = true;
      }
      eth1EventsChannel.onDepositsFromBlock(event);
      lastDeposit = event.getBlockNumber();
    }

    public void depositsComplete() {
      if (genesis.isPresent() && !isGenesisDone) {
        this.eth1EventsChannel.onMinGenesisTimeBlock(genesis.get());
        lastDeposit = genesis.get().getBlockNumber();
        isGenesisDone = true;
      }
    }

    public ReplayDepositsResult done() {
      return new ReplayDepositsResult(lastDeposit, isGenesisDone);
    }
  }
}
