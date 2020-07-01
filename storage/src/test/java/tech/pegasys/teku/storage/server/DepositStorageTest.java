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

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.pow.api.TrackingEth1EventsChannel;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.storage.api.schema.ReplayDepositsResult;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystemArgumentsProvider;
import tech.pegasys.teku.util.async.SafeFuture;

public class DepositStorageTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private DepositStorage depositStorage;

  private final DepositsFromBlockEvent block_99 =
      dataStructureUtil.randomDepositsFromBlockEvent(99L, 10);
  private final MinGenesisTimeBlockEvent genesis_100 =
      dataStructureUtil.randomMinGenesisTimeBlockEvent(100L);
  private final DepositsFromBlockEvent block_100 =
      dataStructureUtil.randomDepositsFromBlockEvent(100L, 10);
  private final DepositsFromBlockEvent block_101 =
      dataStructureUtil.randomDepositsFromBlockEvent(101L, 10);

  @TempDir Path dataDirectory;
  private StorageSystem storageSystem;
  private Database database;
  private TrackingEth1EventsChannel eventsChannel;

  private void setup(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    storageSystem = storageSystemSupplier.get(dataDirectory);
    database = storageSystem.getDatabase();
    eventsChannel = storageSystem.eth1EventsChannel();

    storageSystem.chainUpdater().initializeGenesis();
    depositStorage = storageSystem.createDepositStorage(true);
    depositStorage.start();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldSendGenesisBeforeFirstDeposit(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addMinGenesisTimeBlock(genesis_100);
    database.addDepositsFromBlockEvent(block_101);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();

    assertThat(eventsChannel.getOrderedList()).containsExactly(genesis_100, block_101);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis_100);
    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(block_101.getBlockNumber().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldNotLoadFromStorageIfDisabled(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    depositStorage = DepositStorage.create(eventsChannel, database, false);
    depositStorage.start();

    database.addMinGenesisTimeBlock(genesis_100);
    database.addDepositsFromBlockEvent(block_101);
    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();

    assertThat(eventsChannel.getOrderedList()).isEmpty();
    assertThat(future.get().getFirstUnprocessedBlockNumber()).isEqualTo(BigInteger.ZERO);
    assertThat(future.get().isPastMinGenesisBlock()).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldReplayDepositsWhenDatabaseIsEmpty(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    depositStorage.start();

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();

    assertThat(eventsChannel.getOrderedList()).isEmpty();
    assertThat(future.get().getLastProcessedBlockNumber()).isEqualTo(BigInteger.valueOf(-1));
    assertThat(future.get().getFirstUnprocessedBlockNumber()).isEqualTo(BigInteger.ZERO);
    assertThat(future.get().isPastMinGenesisBlock()).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldStoreDepositsFromBlockImmediatelyAfterReplay(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block_100);
    depositStorage.start();

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();

    assertThat(eventsChannel.getOrderedList()).containsExactly(block_100);
    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(block_100.getBlockNumber().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isFalse();

    depositStorage.onDepositsFromBlock(block_100); // Should ignore
    depositStorage.onDepositsFromBlock(block_101); // Should store
    try (Stream<DepositsFromBlockEvent> deposits = database.streamDepositsFromBlocks()) {
      assertThat(deposits).containsExactly(block_100, block_101);
    }
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  void shouldNotReplayMoreThanOnce(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block_100);
    depositStorage.start();

    SafeFuture<ReplayDepositsResult> firstReplay = depositStorage.replayDepositEvents();
    assertThat(firstReplay).isCompleted();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block_100);

    SafeFuture<ReplayDepositsResult> secondReplay = depositStorage.replayDepositEvents();
    assertThat(secondReplay).isCompleted();
    assertThat(secondReplay.get()).isSameAs(firstReplay.get());
    assertThat(eventsChannel.getOrderedList()).containsExactly(block_100);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldSendGenesisAfterFirstDeposit(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block_99);
    database.addMinGenesisTimeBlock(genesis_100);
    database.addDepositsFromBlockEvent(block_101);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block_99, genesis_100, block_101);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis_100);

    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(block_101.getBlockNumber().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldReplayMultipleDeposits(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block_100);
    database.addDepositsFromBlockEvent(block_101);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block_100, block_101);
    assertThat(eventsChannel.getGenesis()).isNull();
    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(block_101.getBlockNumber().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldSendBlockThenGenesisWhenBlockNumberIsTheSame(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block_100);
    database.addMinGenesisTimeBlock(genesis_100);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block_100, genesis_100);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis_100);

    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(genesis_100.getBlockNumber().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldJustSendGenesis(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addMinGenesisTimeBlock(genesis_100);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(genesis_100);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis_100);

    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(genesis_100.getBlockNumber().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldSendDepositsThenGenesis(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block_99);
    database.addMinGenesisTimeBlock(genesis_100);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future.isDone()).isTrue();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block_99, genesis_100);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis_100);

    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(genesis_100.getBlockNumber().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }
}
