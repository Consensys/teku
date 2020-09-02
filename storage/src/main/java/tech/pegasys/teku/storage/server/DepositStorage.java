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

import com.google.common.base.Suppliers;
import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.pow.exception.InvalidDepositEventsException;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.schema.ReplayDepositsResult;

public class DepositStorage implements Eth1DepositStorageChannel, Eth1EventsChannel {
  private static final Logger LOG = LogManager.getLogger();
  private static final BigInteger NEGATIVE_ONE = BigInteger.valueOf(-1L);

  private final Database database;
  private final Eth1EventsChannel eth1EventsChannel;
  private volatile Optional<BigInteger> lastReplayedBlock = Optional.empty();
  private final Supplier<SafeFuture<ReplayDepositsResult>> replayResult;
  private final boolean eth1DepositsFromStorageEnabled;

  private DepositStorage(
      final Eth1EventsChannel eth1EventsChannel,
      final Database database,
      final boolean eth1DepositsFromStorageEnabled) {
    this.eth1EventsChannel = eth1EventsChannel;
    this.database = database;
    this.replayResult = Suppliers.memoize(() -> SafeFuture.of(this::replayDeposits));
    this.eth1DepositsFromStorageEnabled = eth1DepositsFromStorageEnabled;
  }

  public static DepositStorage create(
      final Eth1EventsChannel eth1EventsChannel,
      final Database database,
      final boolean eth1DepositsFromStorageEnabled) {
    return new DepositStorage(eth1EventsChannel, database, eth1DepositsFromStorageEnabled);
  }

  @Override
  public SafeFuture<ReplayDepositsResult> replayDepositEvents() {
    return replayResult.get();
  }

  private ReplayDepositsResult replayDeposits() {
    if (!eth1DepositsFromStorageEnabled) {
      lastReplayedBlock = Optional.of(NEGATIVE_ONE);
      return ReplayDepositsResult.empty();
    }

    final DepositSequencer depositSequencer =
        new DepositSequencer(eth1EventsChannel, database.getMinGenesisTimeBlock());
    try (Stream<DepositsFromBlockEvent> eventStream = database.streamDepositsFromBlocks()) {
      eventStream.forEach(depositSequencer::depositEvent);
    }
    ReplayDepositsResult result = depositSequencer.depositsComplete();
    lastReplayedBlock = Optional.of(result.getLastProcessedBlockNumber());
    return result;
  }

  private boolean shouldProcessEvent(final BigInteger blockNumber) {
    return lastReplayedBlock.map(startBlock -> startBlock.compareTo(blockNumber) < 0).orElse(false);
  }

  @Override
  public void onDepositsFromBlock(final DepositsFromBlockEvent event) {
    if (shouldProcessEvent(event.getBlockNumber().bigIntegerValue())) {
      database.addDepositsFromBlockEvent(event);
    }
  }

  @Override
  public void onMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    if (shouldProcessEvent(event.getBlockNumber().bigIntegerValue())) {
      database.addMinGenesisTimeBlock(event);
    }
  }

  private static class DepositSequencer {
    private final Eth1EventsChannel eth1EventsChannel;
    private final Optional<MinGenesisTimeBlockEvent> genesis;
    private boolean isGenesisDone = false;
    private BigInteger lastDepositBlockNumber = NEGATIVE_ONE;
    private Optional<UInt64> lastDepositIndex = Optional.empty();

    public DepositSequencer(
        final Eth1EventsChannel eventChannel, final Optional<MinGenesisTimeBlockEvent> genesis) {
      this.eth1EventsChannel = eventChannel;
      this.genesis = genesis;
    }

    public void depositEvent(final DepositsFromBlockEvent event) {
      LOG.trace(
          "Process deposits {} - {}", event.getFirstDepositIndex(), event.getLastDepositIndex());
      if (genesis.isPresent()
          && !isGenesisDone
          && genesis.get().getBlockNumber().compareTo(event.getBlockNumber()) < 0) {
        this.eth1EventsChannel.onMinGenesisTimeBlock(genesis.get());
        isGenesisDone = true;
      }
      validateDepositEvent(event);
      eth1EventsChannel.onDepositsFromBlock(event);
      lastDepositIndex = Optional.of(event.getLastDepositIndex());
      lastDepositBlockNumber = event.getBlockNumber().bigIntegerValue();
    }

    private void validateDepositEvent(final DepositsFromBlockEvent event) {
      final UInt64 expectedDepositIndex =
          lastDepositIndex.map(UInt64::increment).orElse(UInt64.ZERO);
      if (!event.getFirstDepositIndex().equals(expectedDepositIndex)) {
        throw InvalidDepositEventsException.expectedDepositAtIndex(
            expectedDepositIndex, event.getFirstDepositIndex());
      }
    }

    public ReplayDepositsResult depositsComplete() {
      LOG.trace("Finish replaying deposit storage");
      if (genesis.isPresent() && !isGenesisDone) {
        this.eth1EventsChannel.onMinGenesisTimeBlock(genesis.get());
        lastDepositBlockNumber = genesis.get().getBlockNumber().bigIntegerValue();
        isGenesisDone = true;
      }
      final Optional<BigInteger> depositIndex = lastDepositIndex.map(UInt64::bigIntegerValue);
      return ReplayDepositsResult.create(lastDepositBlockNumber, depositIndex, isGenesisDone);
    }
  }
}
