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

package tech.pegasys.teku.storage.server.rocksdb;

import static java.util.stream.Collectors.toList;

import com.google.common.collect.Streams;
import com.google.common.primitives.UnsignedLong;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.storageSystem.FileBackedStorageSystem;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.util.config.StateStorageMode;

public class V3RocksDbDatabaseTest extends AbstractRocksDbDatabaseTest {

  @Override
  protected StorageSystem createStorageSystem(
      final File tempDir, final StateStorageMode storageMode) {
    return FileBackedStorageSystem.createV3StorageSystem(tempDir.toPath(), storageMode);
  }

  @Test
  public void shouldHandleRestartWithUnrecoverableForkBlocks_archive(@TempDir final Path tempDir)
      throws Exception {
    testShouldHandleRestartWithUnrecoverableForkBlocks(tempDir, StateStorageMode.ARCHIVE);
  }

  @Test
  public void shouldHandleRestartWithUnrecoverableForkBlocks_prune(@TempDir final Path tempDir)
      throws Exception {
    testShouldHandleRestartWithUnrecoverableForkBlocks(tempDir, StateStorageMode.PRUNE);
  }

  @Test
  public void shouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart__archive(
      @TempDir final Path tempDir) throws Exception {
    testShouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart(tempDir, StateStorageMode.ARCHIVE);
  }

  @Test
  public void shouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart__prune(
      @TempDir final Path tempDir) throws Exception {
    testShouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart(tempDir, StateStorageMode.PRUNE);
  }

  private void testShouldHandleRestartWithUnrecoverableForkBlocks(
      @TempDir final Path tempDir, final StateStorageMode storageMode) throws Exception {
    // Setup chains
    // Both chains share block up to slot 3
    final ChainBuilder primaryChain = ChainBuilder.create(VALIDATOR_KEYS);
    primaryChain.generateGenesis();
    primaryChain.generateBlocksUpToSlot(3);
    final ChainBuilder forkChain = primaryChain.fork();
    // Primary chain's next block is at 7
    final SignedBlockAndState finalizedBlock = primaryChain.generateBlockAtSlot(7);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(primaryChain.getBlockAtSlot(7));
    final UnsignedLong firstHotBlockSlot =
        finalizedCheckpoint.getEpochStartSlot().plus(UnsignedLong.ONE);
    primaryChain.generateBlockAtSlot(firstHotBlockSlot);
    // Fork chain's next block is at 6
    forkChain.generateBlockAtSlot(6);
    forkChain.generateBlockAtSlot(firstHotBlockSlot);

    // Setup database
    createStorage(tempDir.toFile(), storageMode);
    initGenesis();

    final Set<SignedBlockAndState> allBlocksAndStates =
        Streams.concat(primaryChain.streamBlocksAndStates(), forkChain.streamBlocksAndStates())
            .collect(Collectors.toSet());

    // Finalize at block 7, making the fork blocks unavailable
    add(allBlocksAndStates);
    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);

    // Close database and rebuild from disk
    restartStorage();

    // We should be able to access primary hot blocks and state
    final List<SignedBlockAndState> expectedHotBlocksAndStates =
        primaryChain
            .streamBlocksAndStates(finalizedBlock.getSlot(), chainBuilder.getLatestSlot())
            .collect(toList());
    assertHotBlocksAndStatesInclude(expectedHotBlocksAndStates);

    // Fork states should be unavailable
    final List<Bytes32> unavailableBlockRoots =
        forkChain
            .streamBlocksAndStates(4, firstHotBlockSlot.longValue())
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toList());
    final List<UnsignedLong> unavailableBlockSlots =
        forkChain
            .streamBlocksAndStates(4, firstHotBlockSlot.longValue())
            .map(SignedBlockAndState::getSlot)
            .collect(Collectors.toList());
    assertStatesUnavailable(unavailableBlockSlots);
    assertBlocksUnavailable(unavailableBlockRoots);
  }

  private void testShouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart(
      @TempDir final Path tempDir, final StateStorageMode storageMode) throws Exception {
    final long finalizedSlot = 7;
    final int hotBlockCount = 3;
    // Setup chains
    chainBuilder.generateBlocksUpToSlot(finalizedSlot);
    SignedBlockAndState finalizedBlock = chainBuilder.getBlockAndStateAtSlot(finalizedSlot);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(finalizedBlock.getBlock());
    final long firstHotBlockSlot =
        finalizedCheckpoint.getEpochStartSlot().plus(UnsignedLong.ONE).longValue();
    for (int i = 0; i < hotBlockCount; i++) {
      chainBuilder.generateBlockAtSlot(firstHotBlockSlot + i);
    }
    final long lastSlot = chainBuilder.getLatestSlot().longValue();

    // Setup database
    createStorage(tempDir.toFile(), storageMode);
    initGenesis();

    add(chainBuilder.streamBlocksAndStates().collect(Collectors.toSet()));

    // Close database and rebuild from disk
    restartStorage();

    justifyAndFinalizeEpoch(finalizedCheckpoint.getEpoch(), finalizedBlock);

    // We should be able to access hot blocks and state
    final List<SignedBlockAndState> expectedHotBlocksAndStates =
        chainBuilder.streamBlocksAndStates(finalizedSlot, lastSlot).collect(toList());
    assertHotBlocksAndStatesInclude(expectedHotBlocksAndStates);

    final Map<Bytes32, BeaconState> historicalStates =
        chainBuilder
            .streamBlocksAndStates(0, 6)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));

    switch (storageMode) {
      case ARCHIVE:
        assertFinalizedStatesAvailable(historicalStates);
        break;
      case PRUNE:
        assertStatesUnavailable(
            historicalStates.values().stream().map(BeaconState::getSlot).collect(toList()));
        break;
    }
  }
}
