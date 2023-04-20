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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.collect.Lists;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystemArgumentsProvider;

public class ChainStorageTest {
  @TempDir Path dataDirectory;
  private StorageSystem storageSystem;
  private ChainBuilder chainBuilder;
  private ChainStorage chainStorage;

  private final Spec spec = TestSpecFactory.createMinimalDeneb();

  private void setup(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    storageSystem = storageSystemSupplier.get(dataDirectory, spec);
    chainStorage = storageSystem.chainStorage();
    chainBuilder = storageSystem.chainBuilder();

    chainBuilder.generateGenesis();
  }

  @AfterEach
  void tearDown() {
    try {
      if (storageSystem != null) {
        storageSystem.close();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocks_shouldAcceptValidBlocks_startFromAnchorWithBlock(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocks(storageSystemSupplier, false, false);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocks_shouldAcceptValidBlocks_startFromAnchorWithoutBlock(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocks(storageSystemSupplier, true, false);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocks_shouldAcceptValidBlocks_inBatches(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocks(storageSystemSupplier, true, true);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocksAndBlobSidecars_shouldAcceptValidBlocks_startFromAnchorWithBlock(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocksAndBlobsSidecars(storageSystemSupplier, false, false);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocksAndBlobSidecars_shouldAcceptValidBlocks_startFromAnchorWithoutBlock(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocksAndBlobsSidecars(storageSystemSupplier, true, false);
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocksAndBlobSidecars_shouldAcceptValidBlocks_inBatches(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    testOnFinalizedBlocksAndBlobsSidecars(storageSystemSupplier, true, true);
  }

  public void testOnFinalizedBlocks(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier,
      final boolean initializeWithAnchorStateAlone,
      final boolean executeInBatches) {
    testOnFinalized(storageSystemSupplier, initializeWithAnchorStateAlone, executeInBatches, false);
  }

  public void testOnFinalizedBlocksAndBlobsSidecars(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier,
      final boolean initializeWithAnchorStateAlone,
      final boolean executeInBatches) {
    testOnFinalized(storageSystemSupplier, initializeWithAnchorStateAlone, executeInBatches, true);
  }

  public void testOnFinalized(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier,
      final boolean initializeWithAnchorStateAlone,
      final boolean executeInBatches,
      final boolean testBlobSidecars) {
    setup(storageSystemSupplier);

    // Build small chain
    chainBuilder.generateBlocksUpToSlot(spec.slotsPerEpoch(ZERO) * 3L);

    // Retrieve anchor data
    final Checkpoint anchorCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(3);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.getBlockAndState(anchorCheckpoint.getRoot()).orElseThrow();

    // Initialize from intermediate anchor point
    final AnchorPoint anchorPoint;
    final long firstMissingBlockSlot;
    if (initializeWithAnchorStateAlone) {
      anchorPoint =
          AnchorPoint.create(
              spec, anchorCheckpoint, anchorBlockAndState.getState(), Optional.empty());
      storageSystem.recentChainData().initializeFromAnchorPoint(anchorPoint, ZERO);
      firstMissingBlockSlot = anchorBlockAndState.getSlot().longValue();
    } else {
      anchorPoint = AnchorPoint.create(spec, anchorCheckpoint, anchorBlockAndState);
      storageSystem.recentChainData().initializeFromAnchorPoint(anchorPoint, ZERO);
      firstMissingBlockSlot = anchorBlockAndState.getSlot().minus(1).longValue();
    }

    // Now try to store missing historical blocks and blob sidecars
    final List<SignedBeaconBlock> missingHistoricalBlocks =
        chainBuilder
            .streamBlocksAndStates(0, firstMissingBlockSlot)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    final Map<UInt64, List<BlobSidecar>> missingHistoricalBlobSidecars =
        chainBuilder
            .streamBlobSidecars(0, firstMissingBlockSlot)
            .collect(Collectors.groupingBy(BlobSidecar::getSlot));

    // Sanity check - blocks and blob sidecars should be unavailable initially
    for (SignedBeaconBlock missingHistoricalBlock : missingHistoricalBlocks) {
      final SafeFuture<Optional<SignedBeaconBlock>> blockResult =
          chainStorage.getBlockByBlockRoot(missingHistoricalBlock.getRoot());
      assertThatSafeFuture(blockResult).isCompletedWithEmptyOptional();
      final SafeFuture<List<SlotAndBlockRootAndBlobIndex>> sidecarKeysResult =
          chainStorage.getBlobSidecarKeys(
              missingHistoricalBlock.getSlot(),
              missingHistoricalBlock.getSlot(),
              UInt64.valueOf(Long.MAX_VALUE));
      assertThatSafeFuture(sidecarKeysResult).isCompletedWithValueMatching(List::isEmpty);
    }

    final Map<UInt64, List<BlobSidecar>> finalizedBlobSidecars =
        testBlobSidecars ? missingHistoricalBlobSidecars : Map.of();
    if (executeInBatches) {
      final int batchSize = missingHistoricalBlocks.size() / 3;
      final List<List<SignedBeaconBlock>> batches =
          Lists.partition(missingHistoricalBlocks, batchSize);
      for (int i = batches.size() - 1; i >= 0; i--) {
        final List<SignedBeaconBlock> batch = batches.get(i);
        chainStorage.onFinalizedBlocks(batch, finalizedBlobSidecars).ifExceptionGetsHereRaiseABug();
      }
    } else {
      chainStorage
          .onFinalizedBlocks(missingHistoricalBlocks, finalizedBlobSidecars)
          .ifExceptionGetsHereRaiseABug();
    }

    // Verify blocks and blob sidecars are now available
    for (SignedBeaconBlock missingHistoricalBlock : missingHistoricalBlocks) {
      final SafeFuture<Optional<SignedBeaconBlock>> blockResult =
          chainStorage.getBlockByBlockRoot(missingHistoricalBlock.getRoot());
      assertThatSafeFuture(blockResult).isCompletedWithOptionalContaining(missingHistoricalBlock);
      final SafeFuture<List<BlobSidecar>> sidecarsResult =
          chainStorage
              .getBlobSidecarKeys(
                  missingHistoricalBlock.getSlot(),
                  missingHistoricalBlock.getSlot(),
                  UInt64.valueOf(Long.MAX_VALUE))
              .thenCompose(
                  list ->
                      SafeFuture.collectAll(
                              list.stream().map(key -> chainStorage.getBlobSidecar(key)))
                          .thenApply(
                              listOfOptionals ->
                                  listOfOptionals.stream()
                                      .filter(Optional::isPresent)
                                      .map(Optional::get)
                                      .collect(Collectors.toList())));
      if (testBlobSidecars) {
        // verify blobs sidecar for a block is available
        assertThatSafeFuture(sidecarsResult)
            .isCompletedWithValueMatching(
                list ->
                    list.equals(
                        missingHistoricalBlobSidecars.getOrDefault(
                            missingHistoricalBlock.getSlot(), Collections.emptyList())));
      } else {
        assertThatSafeFuture(sidecarsResult).isCompletedWithValueMatching(List::isEmpty);
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocks_nonMatchingBlocks(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    setup(storageSystemSupplier);
    final int epochs = 3;
    final int chainSize = spec.slotsPerEpoch(ZERO) * epochs;

    // Create fork
    final ChainBuilder forkBuilder = chainBuilder.fork();
    // Skip a block to create a divergent chain
    forkBuilder.generateBlockAtSlot(2);
    forkBuilder.generateBlocksUpToSlot(chainSize);

    // Build small chain
    chainBuilder.generateBlocksUpToSlot(chainSize);
    // Retrieve anchor data
    final Checkpoint anchorCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(epochs);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.getBlockAndState(anchorCheckpoint.getRoot()).orElseThrow();

    // Initialize from intermediate anchor
    final AnchorPoint anchorPoint =
        AnchorPoint.create(
            spec, anchorCheckpoint, anchorBlockAndState.getState(), Optional.empty());
    storageSystem.recentChainData().initializeFromAnchorPoint(anchorPoint, ZERO);
    final long firstMissingBlockSlot = anchorBlockAndState.getSlot().longValue();

    // Try to save non-matching fork blocks
    final List<SignedBeaconBlock> invalidBlocks =
        forkBuilder
            .streamBlocksAndStates(0, firstMissingBlockSlot)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());
    final SafeFuture<Void> result = chainStorage.onFinalizedBlocks(invalidBlocks, Map.of());
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Blocks must be contiguous with the earliest known block");
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocks_missingBlock(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    setup(storageSystemSupplier);
    final int epochs = 3;
    final int chainSize = spec.slotsPerEpoch(ZERO) * epochs;

    // Build small chain
    chainBuilder.generateBlocksUpToSlot(chainSize);
    // Retrieve anchor data
    final Checkpoint anchorCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(epochs);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.getBlockAndState(anchorCheckpoint.getRoot()).orElseThrow();

    // Initialize from intermediate anchor
    final AnchorPoint anchorPoint =
        AnchorPoint.create(
            spec, anchorCheckpoint, anchorBlockAndState.getState(), Optional.empty());
    storageSystem.recentChainData().initializeFromAnchorPoint(anchorPoint, ZERO);
    final long firstMissingBlockSlot = anchorBlockAndState.getSlot().longValue();

    // Get set of blocks to save
    final List<SignedBeaconBlock> blocks =
        chainBuilder
            .streamBlocksAndStates(0, firstMissingBlockSlot)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());
    // Remove a block from the middle
    blocks.remove(blocks.size() / 2);

    final SafeFuture<Void> result = chainStorage.onFinalizedBlocks(blocks, Map.of());
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Blocks must be contiguous with the earliest known block");
  }

  @ParameterizedTest(name = "{0}")
  @ArgumentsSource(StorageSystemArgumentsProvider.class)
  public void onFinalizedBlocks_gapBetweenBatchAndEarliestKnownBlock(
      final String storageType,
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    setup(storageSystemSupplier);
    final int epochs = 3;
    final int chainSize = spec.slotsPerEpoch(ZERO) * epochs;

    // Build small chain
    chainBuilder.generateBlocksUpToSlot(chainSize);
    // Retrieve anchor data
    final Checkpoint anchorCheckpoint = chainBuilder.getCurrentCheckpointForEpoch(epochs);
    final SignedBlockAndState anchorBlockAndState =
        chainBuilder.getBlockAndState(anchorCheckpoint.getRoot()).orElseThrow();

    // Initialize from intermediate anchor
    final AnchorPoint anchorPoint =
        AnchorPoint.create(
            spec, anchorCheckpoint, anchorBlockAndState.getState(), Optional.empty());
    storageSystem.recentChainData().initializeFromAnchorPoint(anchorPoint, ZERO);
    final long firstMissingBlockSlot = anchorBlockAndState.getSlot().longValue();

    // Get set of blocks to save
    final List<SignedBeaconBlock> blocks =
        chainBuilder
            .streamBlocksAndStates(0, firstMissingBlockSlot)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());
    // Remove a block from the end
    blocks.remove(blocks.size() - 1);

    final SafeFuture<Void> result = chainStorage.onFinalizedBlocks(blocks, Map.of());
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Blocks must be contiguous with the earliest known block");
  }
}
