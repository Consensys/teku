/*
 * Copyright ConsenSys Software Inc., 2022
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

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import tech.pegasys.teku.beacon.pow.api.TrackingEth1EventsChannel;
import tech.pegasys.teku.ethereum.pow.api.Deposit;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.InvalidDepositEventsException;
import tech.pegasys.teku.ethereum.pow.api.MinGenesisTimeBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.schema.ReplayDepositsResult;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystemArgumentsProvider;

public class DepositStorageTest {
  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private DepositStorage depositStorage;

  private final MinGenesisTimeBlockEvent genesis100 =
      dataStructureUtil.randomMinGenesisTimeBlockEvent(100L);

  private final DepositsFromBlockEvent block99 =
      dataStructureUtil.randomDepositsFromBlockEvent(99L, 0, 10);
  private final DepositsFromBlockEvent block100 =
      dataStructureUtil.randomDepositsFromBlockEvent(100L, 10, 20);
  private final DepositsFromBlockEvent block101 =
      dataStructureUtil.randomDepositsFromBlockEvent(101L, 20, 21);

  @TempDir Path dataDirectory;
  private StorageSystem storageSystem;
  private Database database;
  private TrackingEth1EventsChannel eventsChannel;

  private void setup(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    storageSystem = storageSystemSupplier.get(dataDirectory, spec);
    database = storageSystem.database();
    eventsChannel = storageSystem.eth1EventsChannel();

    depositStorage = storageSystem.createDepositStorage(false);
  }

  @AfterEach
  void cleanUp() throws Exception {
    database.close();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldRecordAndRetrieveDepositEvents(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    setup(storageSystemSupplier);

    final UInt64 firstBlock = dataStructureUtil.randomUInt64();
    final DepositsFromBlockEvent event1 =
        dataStructureUtil.randomDepositsFromBlockEvent(firstBlock, 0, 10);
    final DepositsFromBlockEvent event2 =
        dataStructureUtil.randomDepositsFromBlockEvent(firstBlock.plus(ONE), 10, 11);

    database.addDepositsFromBlockEvent(event1);
    database.addDepositsFromBlockEvent(event2);
    try (Stream<DepositsFromBlockEvent> events = database.streamDepositsFromBlocks()) {
      assertThat(events.collect(toList())).containsExactly(event1, event2);
    }
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldSendGenesisBeforeFirstDeposit(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);

    final DepositsFromBlockEvent postGenesisDeposits =
        dataStructureUtil.randomDepositsFromBlockEvent(101L, 0, 21);
    database.addMinGenesisTimeBlock(genesis100);
    database.addDepositsFromBlockEvent(postGenesisDeposits);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompleted();

    assertThat(eventsChannel.getOrderedList()).containsExactly(genesis100, postGenesisDeposits);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis100);
    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(postGenesisDeposits.getBlockNumber().bigIntegerValue());
    assertThat(future.get().getLastProcessedDepositIndex())
        .hasValue(postGenesisDeposits.getLastDepositIndex().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  void shouldBePastMinGenesisBlockWhenInitialAnchorIsKnown(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    // Even though we have no deposits, if we have a genesis state then we can skip finding the
    // block that satisfies the minimum genesis time because we won't ever have to create genesis
    storageSystem.chainUpdater().initializeGenesis();

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompleted();

    assertThat(eventsChannel.getOrderedList()).isEmpty();
    assertThat(eventsChannel.getGenesis()).isNull();
    assertThat(future.get().getLastProcessedBlockNumber()).isEqualTo(BigInteger.valueOf(-1));
    assertThat(future.get().getLastProcessedDepositIndex()).isEmpty();
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldReplayDepositsWhenDatabaseIsEmpty(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompleted();

    assertThat(eventsChannel.getOrderedList()).isEmpty();
    assertThat(future.get().getLastProcessedBlockNumber()).isEqualTo(BigInteger.valueOf(-1));
    assertThat(future.get().getLastProcessedDepositIndex()).isEmpty();
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
    database.addDepositsFromBlockEvent(block99);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompleted();

    assertThat(eventsChannel.getOrderedList()).containsExactly(block99);
    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(block99.getBlockNumber().bigIntegerValue());
    assertThat(future.get().getLastProcessedDepositIndex())
        .hasValue(block99.getLastDepositIndex().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isFalse();

    depositStorage.onDepositsFromBlock(block99); // Should ignore
    depositStorage.onDepositsFromBlock(block100); // Should store
    try (Stream<DepositsFromBlockEvent> deposits = database.streamDepositsFromBlocks()) {
      assertThat(deposits).containsExactly(block99, block100);
    }
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  void shouldNotReplayMoreThanOnce(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block99);

    SafeFuture<ReplayDepositsResult> firstReplay = depositStorage.replayDepositEvents();
    assertThat(firstReplay).isCompleted();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block99);

    SafeFuture<ReplayDepositsResult> secondReplay = depositStorage.replayDepositEvents();
    assertThat(secondReplay).isCompleted();
    assertThat(secondReplay.get()).isSameAs(firstReplay.get());
    assertThat(eventsChannel.getOrderedList()).containsExactly(block99);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldSendGenesisAfterFirstDeposit(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block99);
    database.addDepositsFromBlockEvent(block100);
    database.addMinGenesisTimeBlock(genesis100);
    database.addDepositsFromBlockEvent(block101);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompleted();
    assertThat(eventsChannel.getOrderedList())
        .containsExactly(block99, block100, genesis100, block101);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis100);

    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(block101.getBlockNumber().bigIntegerValue());
    assertThat(future.get().getLastProcessedDepositIndex())
        .hasValue(block101.getLastDepositIndex().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldReplayMultipleDeposits(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block99);
    database.addDepositsFromBlockEvent(block100);
    database.addDepositsFromBlockEvent(block101);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompleted();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block99, block100, block101);
    assertThat(eventsChannel.getGenesis()).isNull();
    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(block101.getBlockNumber().bigIntegerValue());
    assertThat(future.get().getLastProcessedDepositIndex())
        .hasValue(block101.getLastDepositIndex().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isFalse();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldFailIfDepositEventAreNotContiguous(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block99);
    // Deposits from block 100 are skipped
    database.addDepositsFromBlockEvent(block101);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasCauseInstanceOf(InvalidDepositEventsException.class);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldFailIfStoredDepositsEventIsMissingDeposits(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    setup(storageSystemSupplier);
    final DepositsFromBlockEvent invalidEvent =
        new UnsafeDepositsFromBlockEvent(
            block99.getBlockNumber(),
            block99.getBlockHash(),
            block99.getBlockTimestamp(),
            List.of(
                dataStructureUtil.randomDepositEvent(UInt64.valueOf(0)),
                // Deposit at index 1 is skipped
                dataStructureUtil.randomDepositEvent(UInt64.valueOf(2))));
    database.addDepositsFromBlockEvent(invalidEvent);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasCauseInstanceOf(InvalidDepositEventsException.class);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldFailIfDepositEventsDoNotStartAtIndex0(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    setup(storageSystemSupplier);
    // Missing deposits from block_99
    database.addDepositsFromBlockEvent(block100);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasCauseInstanceOf(InvalidDepositEventsException.class);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldSendBlockThenGenesisWhenBlockNumberIsTheSame(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block99);
    database.addDepositsFromBlockEvent(block100);
    database.addMinGenesisTimeBlock(genesis100);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompleted();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block99, block100, genesis100);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis100);

    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(genesis100.getBlockNumber().bigIntegerValue());
    assertThat(future.get().getLastProcessedDepositIndex())
        .hasValue(block100.getLastDepositIndex().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldJustSendGenesis(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addMinGenesisTimeBlock(genesis100);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompleted();
    assertThat(eventsChannel.getOrderedList()).containsExactly(genesis100);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis100);

    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(genesis100.getBlockNumber().bigIntegerValue());
    assertThat(future.get().getLastProcessedDepositIndex()).isEmpty();
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldSendDepositsThenGenesis(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block99);
    database.addMinGenesisTimeBlock(genesis100);

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompleted();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block99, genesis100);
    assertThat(eventsChannel.getGenesis()).isEqualToComparingFieldByField(genesis100);

    assertThat(future.get().getLastProcessedBlockNumber())
        .isEqualTo(genesis100.getBlockNumber().bigIntegerValue());
    assertThat(future.get().getLastProcessedDepositIndex())
        .hasValue(block99.getLastDepositIndex().bigIntegerValue());
    assertThat(future.get().isPastMinGenesisBlock()).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldNotRemoveDepositsWhenDepositSnapshotStorageNotEnabled(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    database.addDepositsFromBlockEvent(block99);
    database.addDepositsFromBlockEvent(block100);
    database.addDepositsFromBlockEvent(block101);

    SafeFuture<Boolean> removeFuture = depositStorage.removeDepositEvents();
    assertThat(removeFuture).isCompleted();
    assertThat(removeFuture.get()).isFalse();

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompleted();
    assertThat(eventsChannel.getOrderedList()).containsExactly(block99, block100, block101);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void shouldRemoveDepositsWhenDepositSnapshotStorageEnabled(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier)
      throws ExecutionException, InterruptedException {
    setup(storageSystemSupplier);
    depositStorage = storageSystem.createDepositStorage(true);
    database.addDepositsFromBlockEvent(block99);
    database.addDepositsFromBlockEvent(block100);
    database.addDepositsFromBlockEvent(block101);

    SafeFuture<Boolean> removeFuture = depositStorage.removeDepositEvents();
    assertThat(removeFuture).isCompleted();
    assertThat(removeFuture.get()).isTrue();

    SafeFuture<ReplayDepositsResult> future = depositStorage.replayDepositEvents();
    assertThat(future).isCompleted();
    assertThat(eventsChannel.getOrderedList()).isEmpty();
  }

  private static class UnsafeDepositsFromBlockEvent extends DepositsFromBlockEvent {

    protected UnsafeDepositsFromBlockEvent(
        final UInt64 blockNumber,
        final Bytes32 blockHash,
        final UInt64 blockTimestamp,
        final List<Deposit> deposits) {
      super(blockNumber, blockHash, blockTimestamp, deposits);
    }

    @Override
    protected void assertDepositsValid(final List<Deposit> deposits) {
      // Don't do any validation
    }
  }
}
