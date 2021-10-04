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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.collect.Lists;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystemArgumentsProvider;

public class ChainStorageTest {
  @TempDir Path dataDirectory;
  private StorageSystem storageSystem;
  private ChainBuilder chainBuilder;
  private ChainStorage chainStorage;
  private Spec spec = TestSpecFactory.createMinimalPhase0();

  private void setup(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier) {
    storageSystem = storageSystemSupplier.get(dataDirectory);
    chainStorage = storageSystem.chainStorage();
    chainBuilder = storageSystem.chainBuilder();

    chainBuilder.generateGenesis();
  }

  @AfterEach
  void tearDown() {
    try {
      storageSystem.close();
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

  public void testOnFinalizedBlocks(
      final StorageSystemArgumentsProvider.StorageSystemSupplier storageSystemSupplier,
      final boolean initializeWithAnchorStateAlone,
      final boolean executeInBatches) {
    setup(storageSystemSupplier);

    // Build small chain
    chainBuilder.generateBlocksUpToSlot(spec.slotsPerEpoch(ZERO) * 3);
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

    // Now try to store missing historical blocks
    final List<SignedBeaconBlock> missingHistoricalBlocks =
        chainBuilder
            .streamBlocksAndStates(0, firstMissingBlockSlot)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());

    // Sanity check - blocks should be unavailable initially
    for (SignedBeaconBlock missingHistoricalBlock : missingHistoricalBlocks) {
      final SafeFuture<Optional<SignedBeaconBlock>> result =
          chainStorage.getBlockByBlockRoot(missingHistoricalBlock.getRoot());
      assertThatSafeFuture(result).isCompletedWithEmptyOptional();
    }

    if (executeInBatches) {
      final int batchSize = missingHistoricalBlocks.size() / 3;
      final List<List<SignedBeaconBlock>> batches =
          Lists.partition(missingHistoricalBlocks, batchSize);
      for (int i = batches.size() - 1; i >= 0; i--) {
        final List<SignedBeaconBlock> batch = batches.get(i);
        chainStorage.onFinalizedBlocks(batch).reportExceptions();
      }
    } else {
      chainStorage.onFinalizedBlocks(missingHistoricalBlocks).reportExceptions();
    }

    // Verify blocks are now available
    for (SignedBeaconBlock missingHistoricalBlock : missingHistoricalBlocks) {
      final SafeFuture<Optional<SignedBeaconBlock>> result =
          chainStorage.getBlockByBlockRoot(missingHistoricalBlock.getRoot());
      assertThatSafeFuture(result).isCompletedWithOptionalContaining(missingHistoricalBlock);
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
    final SafeFuture<Void> result = chainStorage.onFinalizedBlocks(invalidBlocks);
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

    final SafeFuture<Void> result = chainStorage.onFinalizedBlocks(blocks);
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

    final SafeFuture<Void> result = chainStorage.onFinalizedBlocks(blocks);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Blocks must be contiguous with the earliest known block");
  }
}
