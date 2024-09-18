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

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.eip7594.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnIdentifier;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetriever;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DataColumnSidecarRetrieverStub;
import tech.pegasys.teku.statetransition.datacolumns.retriever.DelayedDataColumnSidecarRetriever;

@SuppressWarnings("JavaCase")
public class DasCustodySyncTest {

  static final int MAX_AVERAGE_COLUMN_DB_READS_PER_SLOT = 30;
  static final int MAX_AVERAGE_BLOCK_DB_READS_PER_SLOT = 30;

  final Spec spec =
      TestSpecFactory.createMinimalEip7594(
          builder ->
              builder.eip7594Builder(
                  dasBuilder ->
                      dasBuilder
                          .dataColumnSidecarSubnetCount(4)
                          .numberOfColumns(8)
                          .custodyRequirement(2)
                          .minEpochsForDataColumnSidecarsRequests(64)));

  final DasCustodyStand custodyStand =
      DasCustodyStand.builder(spec)
          .withAsyncDb(ofMillis(1))
          .withAsyncBlockResolver(ofMillis(2))
          .build();
  final int maxSyncRequests = 32;
  final int minSyncRequests = 8;
  final DataColumnSidecarRetrieverStub retrieverStub = new DataColumnSidecarRetrieverStub();
  final DataColumnSidecarRetriever asyncRetriever =
      new DelayedDataColumnSidecarRetriever(
          retrieverStub, custodyStand.stubAsyncRunner, ofMillis(0));
  final DasCustodySync dasCustodySync =
      new DasCustodySync(custodyStand.custody, asyncRetriever, maxSyncRequests, minSyncRequests);

  final int epochLength = spec.slotsPerEpoch(UInt64.ZERO);

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

    SignedBeaconBlock block_1 = custodyStand.createBlockWithBlobs(1);
    custodyStand.blockResolver.addBlock(block_1.getMessage());
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
    int startSlot = 1000;
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

    List<DataColumnSlotAndIdentifier> missingColumns =
        await(custodyStand.custody.retrieveMissingColumns().toList());
    assertThat(missingColumns).isEmpty();
    assertAllCustodyColumnsPresent();

    assertThat(await(custodyStand.db.getFirstCustodyIncompleteSlot()))
        .hasValueSatisfying(slot -> assertThat(slot.intValue()).isGreaterThan(1900));
  }

  @Test
  void emptyBlockSeriesShouldNotPreventSyncing() {
    int startSlot = 1000;

    for (int slot = 0; slot <= startSlot; slot++) {
      SignedBeaconBlock block = custodyStand.createBlockWithoutBlobs(slot);
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

    List<DataColumnSlotAndIdentifier> missingColumns =
        await(custodyStand.custody.retrieveMissingColumns().toList());
    assertThat(missingColumns).isEmpty();
    assertAllCustodyColumnsPresent();
  }

  @Test
  void emptySlotSeriesShouldNotPreventSyncing() {
    int startSlot = 1000;

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

    List<DataColumnSlotAndIdentifier> missingColumns =
        await(custodyStand.custody.retrieveMissingColumns().toList());
    assertThat(missingColumns).isEmpty();
    assertAllCustodyColumnsPresent();
  }

  //  @Disabled("There are 2 issues at the moment: almost no sync and too many DB queries")
  @Test
  void nonFinalizationShouldNotPreventSyncingAndOverloadDB() {
    // TODO this is too high and needs to be fixed
    int maxAverageColumnDbReadsPerSlot = 400;
    int maxAverageBlockDbReadsPerSlot = 400;

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
        .isLessThan(maxAverageColumnDbReadsPerSlot);
    assertThat(custodyStand.blockResolver.getBlockAccessCounter().get() / 1000)
        .isLessThan(maxAverageBlockDbReadsPerSlot);

    printAndResetStats();

    custodyStand.incCurrentSlot(10);

    advanceTimeGraduallyUntilAllDone();
    printAndResetStats();

    List<DataColumnSlotAndIdentifier> missingColumns =
        await(custodyStand.custody.retrieveMissingColumns().toList());
    assertThat(missingColumns).isEmpty();
    assertAllCustodyColumnsPresent();
  }

  private void addBlockAndSidecars(int slot) {
    SignedBeaconBlock block = custodyStand.createBlockWithBlobs(slot);
    custodyStand.blockResolver.addBlock(block.getMessage());
    List<DataColumnSidecar> columnSidecars = custodyStand.createCustodyColumnSidecars(block);
    columnSidecars.forEach(retrieverStub::addReadyColumnSidecar);
  }

  private void assertAllCustodyColumnsPresent() {
    assertCustodyColumnsPresent(
        custodyStand.getMinCustodySlot().intValue(), custodyStand.getCurrentSlot().intValue());
  }

  private void assertCustodyColumnsPresent(int fromSlot, int tillSlot) {
    for (int slot = fromSlot; slot < tillSlot; slot++) {
      UInt64 uSlot = UInt64.valueOf(slot);
      Optional<BeaconBlock> maybeBlock = custodyStand.blockResolver.getBlockAtSlot(uSlot).join();
      maybeBlock.ifPresent(
          block -> {
            if (custodyStand.hasBlobs(block)) {
              Collection<UInt64> colIndexes = custodyStand.getCustodyColumnIndexes(uSlot);
              for (UInt64 colIndex : colIndexes) {
                Optional<DataColumnSidecar> maybeSidecar =
                    await(
                        custodyStand.custody.getCustodyDataColumnSidecar(
                            new DataColumnIdentifier(block.getRoot(), colIndex)));
                assertThat(maybeSidecar)
                    .isPresent()
                    .hasValueSatisfying(
                        sidecar -> {
                          assertThat(sidecar.getSlot()).isEqualTo(uSlot);
                          assertThat(sidecar.getIndex()).isEqualTo(colIndex);

                          assertThat(sidecar.getBlockRoot()).isEqualTo(block.getRoot());
                        });
              }
            }
          });
    }
  }

  private void advanceTimeGraduallyUntilAllDone() {
    custodyStand.advanceTimeGraduallyUntilAllDone(ofMinutes(1));
  }

  private <T> T await(CompletableFuture<T> future) {
    return await(future, ofMinutes(1));
  }

  private <T> T await(CompletableFuture<T> future, Duration maxWait) {
    for (int i = 0; i < maxWait.toMillis(); i++) {
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
