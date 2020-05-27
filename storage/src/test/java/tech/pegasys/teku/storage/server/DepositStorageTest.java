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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.eventbus.EventBus;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.storage.server.rocksdb.AbstractRocksDbDatabaseTest;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbConfiguration;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbDatabase;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.StateStorageMode;

public class DepositStorageTest extends AbstractRocksDbDatabaseTest {
  private final EventBus eventBus = new EventBus();
  private final TrackingEth1EventsChannel eventsChannel = new TrackingEth1EventsChannel();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private DepositStorage depositStorage;

  @BeforeEach
  public void beforeEach() {
    depositStorage = DepositStorage.create(eventBus, eventsChannel, database);
    depositStorage.start();
  }

  @Test
  public void shouldSendGenesisBeforeFirstDeposit()
      throws ExecutionException, InterruptedException {
    DepositsFromBlockEvent blockEvent = dataStructureUtil.randomDepositsFromBlockEvent(100L, 10);
    MinGenesisTimeBlockEvent genesis = dataStructureUtil.randomMinGenesisTimeBlockEvent(1L);
    database.addDepositsFromBlockEvent(blockEvent);
    database.addMinGenesisTimeBlock(genesis);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();

    assertThat(eventsChannel.getOrderedList()).containsExactly(genesis, blockEvent);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis);
    assertThat(future.get().getBlockNumber().get()).isEqualTo(blockEvent.getBlockNumber());
    assertThat(future.get().isPastGenesisBlock()).isTrue();
    depositStorage.stop();
  }

  @Test
  public void shouldSendGenesisAfterFirstDeposit() throws ExecutionException, InterruptedException {
    DepositsFromBlockEvent blockEvent = dataStructureUtil.randomDepositsFromBlockEvent(100L, 10);
    DepositsFromBlockEvent blockEvent2 = dataStructureUtil.randomDepositsFromBlockEvent(102L, 10);
    MinGenesisTimeBlockEvent genesis = dataStructureUtil.randomMinGenesisTimeBlockEvent(101L);

    database.addDepositsFromBlockEvent(blockEvent);
    database.addDepositsFromBlockEvent(blockEvent2);
    database.addMinGenesisTimeBlock(genesis);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(blockEvent, genesis, blockEvent2);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis);
    assertThat(future.get().getBlockNumber().get()).isEqualTo(blockEvent2.getBlockNumber());
    assertThat(future.get().isPastGenesisBlock()).isTrue();
    depositStorage.stop();
  }

  @Test
  public void shouldReplayMultipleDeposits() throws ExecutionException, InterruptedException {
    DepositsFromBlockEvent blockEvent = dataStructureUtil.randomDepositsFromBlockEvent(100L, 10);
    DepositsFromBlockEvent blockEvent2 = dataStructureUtil.randomDepositsFromBlockEvent(101L, 10);
    database.addDepositsFromBlockEvent(blockEvent);
    database.addDepositsFromBlockEvent(blockEvent2);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(blockEvent, blockEvent2);
    assertThat(eventsChannel.getGenesis()).isNull();
    assertThat(future.get().getBlockNumber().get()).isEqualTo(blockEvent2.getBlockNumber());
    assertThat(future.get().isPastGenesisBlock()).isFalse();
    depositStorage.stop();
  }

  @Test
  public void shouldSendBlockThenGenesisWhenBlockNumberIsTheSame() {
    DepositsFromBlockEvent blockEvent = dataStructureUtil.randomDepositsFromBlockEvent(100L, 10);
    MinGenesisTimeBlockEvent genesis = dataStructureUtil.randomMinGenesisTimeBlockEvent(100L);
    database.addDepositsFromBlockEvent(blockEvent);
    database.addMinGenesisTimeBlock(genesis);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(blockEvent, genesis);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis);
    depositStorage.stop();
  }

  @Test
  public void shouldJustSendGenesis() {
    MinGenesisTimeBlockEvent genesis = dataStructureUtil.randomMinGenesisTimeBlockEvent(100L);
    database.addMinGenesisTimeBlock(genesis);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(genesis);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis);
    depositStorage.stop();
  }

  @Override
  protected Database createDatabase(final File tempDir, final StateStorageMode storageMode) {
    final RocksDbConfiguration config = RocksDbConfiguration.withDataDirectory(tempDir.toPath());
    return RocksDbDatabase.createV3(config, storageMode);
  }

  static class TrackingEth1EventsChannel implements Eth1EventsChannel {
    private final List<Object> orderedList = new ArrayList<>();
    private MinGenesisTimeBlockEvent genesis;

    @Override
    public void onDepositsFromBlock(final DepositsFromBlockEvent event) {
      orderedList.add(event);
    }

    @Override
    public void onMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
      genesis = event;
      orderedList.add(event);
    }

    public MinGenesisTimeBlockEvent getGenesis() {
      return genesis;
    }

    public List<Object> getOrderedList() {
      return orderedList;
    }
  }
}
