/*
 * Copyright Consensys Software Inc., 2024
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

package tech.pegasys.teku.statetransition.datacolumns;

import static java.time.Duration.ofMillis;
import static java.time.Duration.ofMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.stream.AsyncStream;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetrieverStub;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DelayedDataColumnSidecarRetriever;

public class DasCustodySyncTest {

  static final int MAX_AVERAGE_COLUMN_DB_READS_PER_SLOT = 30;
  static final int MAX_AVERAGE_BLOCK_DB_READS_PER_SLOT = 30;

  final Spec spec =
      TestSpecFactory.createMinimalFulu(
          builder ->
              builder.fuluBuilder(
                  fuluBuilder ->
                      fuluBuilder
                          .dataColumnSidecarSubnetCount(4)
                          .cellsPerExtBlob(8)
                          .numberOfColumns(8)
                          .numberOfCustodyGroups(8)
                          .custodyRequirement(2)
                          .validatorCustodyRequirement(0)
                          .balancePerAdditionalCustodyGroup(UInt64.valueOf(32000000000L))
                          .minEpochsForDataColumnSidecarsRequests(64)));

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  final DasCustodyStand custodyStand =
      DasCustodyStand.builder(spec)
          .withAsyncDb(ofMillis(1))
          .withAsyncBlockResolver(ofMillis(2))
          .build();
  final int minSyncRequests = 8;
  final int maxSyncRequests = 32;
  final DataColumnSidecarRetrieverStub retrieverStub = new DataColumnSidecarRetrieverStub();
  final DataColumnSidecarRetriever asyncRetriever =
      new DelayedDataColumnSidecarRetriever(
          retrieverStub, custodyStand.stubAsync.getStubAsyncRunner(), ofMillis(0));
  final MinCustodyPeriodSlotCalculator minCustodyPeriodSlotCalculator =
      mock(MinCustodyPeriodSlotCalculator.class);
  final DasCustodySync dasCustodySync =
      new DasCustodySync(
          custodyStand.custody,
          asyncRetriever,
          minCustodyPeriodSlotCalculator,
          minSyncRequests,
          maxSyncRequests);

  final int epochLength = spec.slotsPerEpoch(UInt64.ZERO);

  @BeforeEach
  public void setup() {
    when(minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(any())).thenReturn(UInt64.ZERO);
  }

  @Test
  void sanityTest() {
    custodyStand.setCurrentSlot(0);
    custodyStand.subscribeToSlotEvents(dasCustodySync);
    dasCustodySync.start();

    advanceTimeGraduallyUntilAllDone();

    printAndResetStats();

    custodyStand.setCurrentSlot(5);

    advanceTimeGraduallyUntilAllDone();

    printAndResetStats();
    assertThat(retrieverStub.requests).isEmpty();

    final SignedBeaconBlock block = custodyStand.createBlockWithBlobs(1);
    custodyStand.blockResolver.addBlock(block.getMessage());
    custodyStand.setCurrentSlot(6);

    advanceTimeGraduallyUntilAllDone();

    printAndResetStats();
    assertThat(retrieverStub.requests).isNotEmpty();

    advanceTimeGraduallyUntilAllDone();

    custodyStand.setCurrentSlot(7);

    printAndResetStats();
  }

  @Test
  void syncFromScratchShouldComplete() {
    final int startSlot = 1000;
    for (int slot = 0; slot <= startSlot; slot++) {
      addBlockAndSidecars(slot);
    }
    printAndResetStats();

    // on start we have 1000 uncustodied slots
    custodyStand.setCurrentSlot(1000);
    custodyStand.subscribeToSlotEvents(dasCustodySync);
    dasCustodySync.start();

    advanceTimeGraduallyUntilAllDone();
    printAndResetStats();

    for (int slot = startSlot + 1; slot <= startSlot + 1000; slot++) {
      addBlockAndSidecars(slot);
      custodyStand.incCurrentSlot(1);
      if (slot % epochLength == 0) {
        int epoch = slot / epochLength;
        custodyStand.setFinalizedEpoch(epoch - 2);
      }
      advanceTimeGraduallyUntilAllDone();
    }

    assertThat(custodyStand.db.getDbReadCounter().get() / 1000)
        .isLessThan(MAX_AVERAGE_COLUMN_DB_READS_PER_SLOT);
    assertThat(custodyStand.blockResolver.getBlockAccessCounter().get() / 1000)
        .isLessThan(MAX_AVERAGE_BLOCK_DB_READS_PER_SLOT);

    printAndResetStats();

    custodyStand.incCurrentSlot(10);

    advanceTimeGraduallyUntilAllDone();
    printAndResetStats();

    final List<DataColumnSlotAndIdentifier> missingColumns =
        await(custodyStand.custody.retrieveMissingColumns().toList());
    assertThat(missingColumns).isEmpty();
    assertAllCustodyColumnsPresent();

    assertThat(await(custodyStand.db.getFirstCustodyIncompleteSlot()))
        .hasValueSatisfying(slot -> assertThat(slot.intValue()).isGreaterThan(1900));
  }

  @Test
  void emptyBlockSeriesShouldNotPreventSyncing() {
    final int startSlot = 1000;

    for (int slot = 0; slot <= startSlot; slot++) {
      final SignedBeaconBlock block = custodyStand.createBlockWithoutBlobs(slot);
      custodyStand.blockResolver.addBlock(block.getMessage());
    }

    custodyStand.setCurrentSlot(1000);
    custodyStand.subscribeToSlotEvents(dasCustodySync);
    dasCustodySync.start();

    advanceTimeGraduallyUntilAllDone();
    printAndResetStats();

    for (int slot = startSlot + 1; slot <= startSlot + 1000; slot++) {
      addBlockAndSidecars(slot);
      custodyStand.incCurrentSlot(1);
      if (slot % epochLength == 0) {
        final int epoch = slot / epochLength;
        custodyStand.setFinalizedEpoch(epoch - 2);
      }
      advanceTimeGraduallyUntilAllDone();
    }

    assertThat(custodyStand.db.getDbReadCounter().get() / 1000)
        .isLessThan(MAX_AVERAGE_COLUMN_DB_READS_PER_SLOT);
    assertThat(custodyStand.blockResolver.getBlockAccessCounter().get() / 1000)
        .isLessThan(MAX_AVERAGE_BLOCK_DB_READS_PER_SLOT);

    printAndResetStats();

    custodyStand.incCurrentSlot(10);

    advanceTimeGraduallyUntilAllDone();
    printAndResetStats();

    final List<DataColumnSlotAndIdentifier> missingColumns =
        await(custodyStand.custody.retrieveMissingColumns().toList());
    assertThat(missingColumns).isEmpty();
    assertAllCustodyColumnsPresent();
  }

  @Test
  void emptySlotSeriesShouldNotPreventSyncing() {
    final int startSlot = 1000;

    custodyStand.setCurrentSlot(1000);
    custodyStand.subscribeToSlotEvents(dasCustodySync);
    dasCustodySync.start();

    advanceTimeGraduallyUntilAllDone();
    printAndResetStats();

    for (int slot = startSlot + 1; slot <= startSlot + 1000; slot++) {
      addBlockAndSidecars(slot);
      custodyStand.incCurrentSlot(1);
      if (slot % epochLength == 0) {
        final int epoch = slot / epochLength;
        custodyStand.setFinalizedEpoch(epoch - 2);
      }
      advanceTimeGraduallyUntilAllDone();
    }

    assertThat(custodyStand.db.getDbReadCounter().get() / 1000)
        .isLessThan(MAX_AVERAGE_COLUMN_DB_READS_PER_SLOT);
    assertThat(custodyStand.blockResolver.getBlockAccessCounter().get() / 1000)
        .isLessThan(MAX_AVERAGE_BLOCK_DB_READS_PER_SLOT);

    printAndResetStats();

    custodyStand.incCurrentSlot(10);
    advanceTimeGraduallyUntilAllDone();

    printAndResetStats();

    final List<DataColumnSlotAndIdentifier> missingColumns =
        await(custodyStand.custody.retrieveMissingColumns().toList());
    assertThat(missingColumns).isEmpty();
    assertAllCustodyColumnsPresent();
  }

  AsyncStream<DataColumnSlotAndIdentifier> testData(
      final List<DataColumnSlotAndIdentifier> columns) {
    return AsyncStream.createUnsafe(columns.iterator());
  }

  @Test
  void syncOutsideOfPeriod() {
    final List<DataColumnSlotAndIdentifier> columns =
        List.of(
            new DataColumnSlotAndIdentifier(
                UInt64.ONE,
                new DataColumnIdentifier(
                    dataStructureUtil.randomBytes32(),
                    dataStructureUtil.randomDataColumnSidecarIndex())),
            new DataColumnSlotAndIdentifier(
                UInt64.valueOf(1025),
                new DataColumnIdentifier(
                    dataStructureUtil.randomBytes32(),
                    dataStructureUtil.randomDataColumnSidecarIndex())));
    final DataColumnSidecarRetriever retriever = mock(DataColumnSidecarRetriever.class);
    final DataColumnSidecarCustody custody = mock(DataColumnSidecarCustody.class);
    when(minCustodyPeriodSlotCalculator.getMinCustodyPeriodSlot(any()))
        .thenReturn(UInt64.valueOf(1024));
    when(custody.retrieveMissingColumns()).thenReturn(testData(columns));
    final DasCustodySync custodySync =
        new DasCustodySync(
            custody, retriever, minCustodyPeriodSlotCalculator, minSyncRequests, maxSyncRequests);
    custodySync.fillUp();
    verify(retriever).retrieve(columns.get(1));
    verifyNoMoreInteractions(retriever);
  }

  @Test
  void nonFinalizationShouldNotPreventSyncingAndOverloadDB() {
    // all the block data will be coming from the proto array, so it's not a problem
    final int maxAverageBlockDbReadsPerSlot = 400;

    custodyStand.setCurrentSlot(0);
    custodyStand.subscribeToSlotEvents(dasCustodySync);
    dasCustodySync.start();

    advanceTimeGraduallyUntilAllDone();
    printAndResetStats();

    for (int slot = 1; slot <= 1000; slot++) {
      addBlockAndSidecars(slot);
      custodyStand.incCurrentSlot(1);
      advanceTimeGraduallyUntilAllDone();
    }

    assertThat(custodyStand.db.getDbReadCounter().get() / 1000)
        .isLessThan(MAX_AVERAGE_COLUMN_DB_READS_PER_SLOT);
    assertThat(custodyStand.blockResolver.getBlockAccessCounter().get() / 1000)
        .isLessThan(maxAverageBlockDbReadsPerSlot);

    printAndResetStats();

    custodyStand.incCurrentSlot(10);

    advanceTimeGraduallyUntilAllDone();
    printAndResetStats();

    final List<DataColumnSlotAndIdentifier> missingColumns =
        await(custodyStand.custody.retrieveMissingColumns().toList());
    assertThat(missingColumns).isEmpty();
    assertAllCustodyColumnsPresent();
  }

  @Test
  void shouldCancelRetrieverRequestWhenCanonicalBlockChanges() {
    custodyStand.setCurrentSlot(0);
    custodyStand.subscribeToSlotEvents(dasCustodySync);
    dasCustodySync.start();

    advanceTimeGraduallyUntilAllDone();

    custodyStand.setCurrentSlot(5);

    advanceTimeGraduallyUntilAllDone();
    assertThat(retrieverStub.requests).isEmpty();

    final SignedBeaconBlock block1 = custodyStand.createBlockWithBlobs(1);
    custodyStand.blockResolver.addBlock(block1.getMessage());
    custodyStand.setCurrentSlot(6);

    advanceTimeGraduallyUntilAllDone();

    final List<DataColumnSidecarRetrieverStub.RetrieveRequest> retrieveRequests =
        new ArrayList<>(retrieverStub.requests);
    assertThat(retrieveRequests).isNotEmpty();

    final SignedBeaconBlock block2 = custodyStand.createBlockWithBlobs(1);
    custodyStand.blockResolver.addBlock(block2.getMessage());
    custodyStand.setCurrentSlot(7);

    advanceTimeGraduallyUntilAllDone();
    assertThat(retrieveRequests)
        .allSatisfy(
            request2 -> {
              assertThat(request2.future()).isCancelled();
            });
    assertThat(retrieverStub.requests).hasSize(retrieveRequests.size() * 2);
  }

  private void addBlockAndSidecars(final int slot) {
    final SignedBeaconBlock block = custodyStand.createBlockWithBlobs(slot);
    custodyStand.blockResolver.addBlock(block.getMessage());
    final List<DataColumnSidecar> columnSidecars = custodyStand.createCustodyColumnSidecars(block);
    columnSidecars.forEach(retrieverStub::addReadyColumnSidecar);
  }

  private void assertAllCustodyColumnsPresent() {
    assertCustodyColumnsPresent(
        custodyStand.getMinCustodySlot().intValue(), custodyStand.getCurrentSlot().intValue());
  }

  private void assertCustodyColumnsPresent(final int fromSlot, final int tillSlot) {
    for (int slot = fromSlot; slot < tillSlot; slot++) {
      final UInt64 uSlot = UInt64.valueOf(slot);
      final Optional<BeaconBlock> maybeBlock =
          custodyStand.blockResolver.getBlockAtSlot(uSlot).join();
      maybeBlock.ifPresent(
          block -> {
            if (custodyStand.hasBlobs(block)) {
              final Collection<UInt64> colIndices = custodyStand.getCustodyColumnIndices();
              for (UInt64 colIndex : colIndices) {
                final Optional<DataColumnSidecar> maybeSidecar =
                    await(
                        custodyStand.custody.getCustodyDataColumnSidecar(
                            new DataColumnSlotAndIdentifier(
                                block.getSlot(), block.getRoot(), colIndex)));
                assertThat(maybeSidecar)
                    .isPresent()
                    .hasValueSatisfying(
                        sidecar -> {
                          assertThat(sidecar.getSlot()).isEqualTo(uSlot);
                          assertThat(sidecar.getIndex()).isEqualTo(colIndex);

                          assertThat(sidecar.getBeaconBlockRoot()).isEqualTo(block.getRoot());
                        });
              }
            }
          });
    }
  }

  private void advanceTimeGraduallyUntilAllDone() {
    custodyStand.advanceTimeGraduallyUntilAllDone(ofMinutes(1));
  }

  private <T> T await(final CompletableFuture<T> future) {
    return await(future, ofMinutes(1));
  }

  private <T> T await(final CompletableFuture<T> future, final Duration maxWait) {
    for (long i = 0; i < maxWait.toMillis(); i++) {
      if (future.isDone()) {
        try {
          return future.get();
        } catch (InterruptedException | ExecutionException e) {
          throw new RuntimeException(e);
        }
      }
      custodyStand.advanceTimeGradually(ofMillis(1));
    }
    throw new AssertionError("Timeout waiting for the future to complete");
  }

  private void printAndResetStats() {
    System.out.println(
        "db: "
            + custodyStand.db.getDbReadCounter().getAndSet(0)
            + ", block: "
            + custodyStand.blockResolver.getBlockAccessCounter().getAndSet(0)
            + ", column requests: "
            + retrieverStub.requests.size());
  }
}
