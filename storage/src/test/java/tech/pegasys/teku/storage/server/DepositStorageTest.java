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

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.storage.api.schema.ReplayDepositsResult;
import tech.pegasys.teku.storage.server.rocksdb.AbstractRocksDbDatabaseTest;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbConfiguration;
import tech.pegasys.teku.storage.server.rocksdb.RocksDbDatabase;
import tech.pegasys.teku.util.async.SafeFuture;
import tech.pegasys.teku.util.config.StateStorageMode;

public class DepositStorageTest extends AbstractRocksDbDatabaseTest {
  private final TrackingEth1EventsChannel eventsChannel = new TrackingEth1EventsChannel();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private DepositStorage depositStorage;

  private final DepositsFromBlockEvent block_99 =
      dataStructureUtil.randomDepositsFromBlockEvent(99L, 10);
  private final MinGenesisTimeBlockEvent genesis_100 =
      dataStructureUtil.randomMinGenesisTimeBlockEvent(100L);
  private final DepositsFromBlockEvent block_100 =
      dataStructureUtil.randomDepositsFromBlockEvent(100L, 10);
  private final DepositsFromBlockEvent block_101 =
      dataStructureUtil.randomDepositsFromBlockEvent(101L, 10);

  @BeforeEach
  public void beforeEach() {
    depositStorage = DepositStorage.create(eventsChannel, database, true);
    depositStorage.start();
  }

  @Test
  public void shouldSendGenesisBeforeFirstDeposit()
      throws ExecutionException, InterruptedException {
    database.addMinGenesisTimeBlock(genesis_100);
    database.addDepositsFromBlockEvent(block_101);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();

    assertThat(eventsChannel.getOrderedList()).containsExactly(genesis_100, block_101);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis_100);
    assertThat(future.get().getBlockNumber())
        .isEqualTo(block_101.getBlockNumber().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @Test
  public void shouldNotLoadFromStorageIfDisabled() throws ExecutionException, InterruptedException {
    depositStorage = DepositStorage.create(eventsChannel, database, false);
    depositStorage.start();

    database.addMinGenesisTimeBlock(genesis_100);
    database.addDepositsFromBlockEvent(block_101);
    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();

    assertThat(eventsChannel.getOrderedList()).isEmpty();
    assertThat(future.get().getBlockNumber()).isEqualTo(BigInteger.ZERO);
    assertThat(future.get().isPastMinGenesisBlock()).isFalse();
  }

  @Test
  public void shouldSendGenesisAfterFirstDeposit() throws ExecutionException, InterruptedException {
    database.addDepositsFromBlockEvent(block_99);
    database.addMinGenesisTimeBlock(genesis_100);
    database.addDepositsFromBlockEvent(block_101);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block_99, genesis_100, block_101);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis_100);

    assertThat(future.get().getBlockNumber())
        .isEqualTo(block_101.getBlockNumber().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @Test
  public void shouldReplayMultipleDeposits() throws ExecutionException, InterruptedException {
    database.addDepositsFromBlockEvent(block_100);
    database.addDepositsFromBlockEvent(block_101);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block_100, block_101);
    assertThat(eventsChannel.getGenesis()).isNull();
    assertThat(future.get().getBlockNumber())
        .isEqualTo(block_101.getBlockNumber().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isFalse();
  }

  @Test
  public void shouldSendBlockThenGenesisWhenBlockNumberIsTheSame()
      throws ExecutionException, InterruptedException {
    database.addDepositsFromBlockEvent(block_100);
    database.addMinGenesisTimeBlock(genesis_100);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block_100, genesis_100);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis_100);

    assertThat(future.get().getBlockNumber())
        .isEqualTo(genesis_100.getBlockNumber().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @Test
  public void shouldJustSendGenesis() throws ExecutionException, InterruptedException {
    database.addMinGenesisTimeBlock(genesis_100);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(genesis_100);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis_100);

    assertThat(future.get().getBlockNumber())
        .isEqualTo(genesis_100.getBlockNumber().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @Test
  public void shouldSendDepositsThenGenesis() throws ExecutionException, InterruptedException {
    database.addDepositsFromBlockEvent(block_99);
    database.addMinGenesisTimeBlock(genesis_100);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block_99, genesis_100);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis_100);

    assertThat(future.get().getBlockNumber())
        .isEqualTo(genesis_100.getBlockNumber().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @Override
  protected Database createDatabase(final File tempDir, final StateStorageMode storageMode) {
    final RocksDbConfiguration config = RocksDbConfiguration.withDataDirectory(tempDir.toPath());
    return RocksDbDatabase.createV3(new StubMetricsSystem(), config, storageMode);
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
