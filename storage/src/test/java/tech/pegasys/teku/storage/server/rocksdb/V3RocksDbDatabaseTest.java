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
import tech.pegasys.teku.metrics.StubMetricsSystem;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.store.StoreFactory;
import tech.pegasys.teku.util.config.StateStorageMode;

public class V3RocksDbDatabaseTest extends AbstractRocksDbDatabaseTest {

  @Override
  protected Database createDatabase(final File tempDir, final StateStorageMode storageMode) {
    final RocksDbConfiguration config = RocksDbConfiguration.withDataDirectory(tempDir.toPath());
    return RocksDbDatabase.createV3(new StubMetricsSystem(), config, storageMode);
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
    final SignedBlockAndState genesis = primaryChain.generateGenesis();
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
    database = setupDatabase(tempDir.toFile(), storageMode);
    store = StoreFactory.getForkChoiceStore(new StubMetricsSystem(), genesis.getState());
    database.storeGenesis(store);

    final Set<SignedBlockAndState> allBlocksAndStates =
        Streams.concat(primaryChain.streamBlocksAndStates(), forkChain.streamBlocksAndStates())
            .collect(Collectors.toSet());

    // Finalize at block 7, making the fork blocks unavailable
    add(allBlocksAndStates);
    finalizeCheckpoint(finalizedCheckpoint);

    // Close database and rebuild from disk
    database.close();
    database = setupDatabase(tempDir.toFile(), storageMode);

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
    assertStatesUnavailable(unavailableBlockRoots);
    assertBlocksUnavailable(unavailableBlockRoots);
  }

  private void testShouldPruneHotBlocksOlderThanFinalizedSlotAfterRestart(
      @TempDir final Path tempDir, final StateStorageMode storageMode) throws Exception {
    final long finalizedSlot = 7;
    final int hotBlockCount = 3;
    // Setup chains
    final ChainBuilder chain = ChainBuilder.create(VALIDATOR_KEYS);
    final SignedBlockAndState genesis = chain.generateGenesis();
    chain.generateBlocksUpToSlot(finalizedSlot);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(chain.getBlockAtSlot(7));
    final long firstHotBlockSlot =
        finalizedCheckpoint.getEpochStartSlot().plus(UnsignedLong.ONE).longValue();
    for (int i = 0; i < hotBlockCount; i++) {
      chainBuilder.generateBlockAtSlot(firstHotBlockSlot + i);
    }
    final long lastSlot = chainBuilder.getLatestSlot().longValue();

    // Setup database
    database = setupDatabase(tempDir.toFile(), storageMode);
    store = StoreFactory.getForkChoiceStore(new StubMetricsSystem(), genesis.getState());
    database.storeGenesis(store);

    add(chain.streamBlocksAndStates().collect(Collectors.toSet()));

    // Close database and rebuild from disk
    database.close();
    database = setupDatabase(tempDir.toFile(), storageMode);

    finalizeCheckpoint(finalizedCheckpoint);

    // We should be able to access hot blocks and state
    final List<SignedBlockAndState> expectedHotBlocksAndStates =
        chain.streamBlocksAndStates(finalizedSlot, lastSlot).collect(toList());
    assertHotBlocksAndStatesInclude(expectedHotBlocksAndStates);

    final Map<Bytes32, BeaconState> historicalStates =
        chain
            .streamBlocksAndStates(0, 6)
            .collect(Collectors.toMap(SignedBlockAndState::getRoot, SignedBlockAndState::getState));

    switch (storageMode) {
      case ARCHIVE:
        assertFinalizedStatesAvailable(historicalStates);
        break;
      case PRUNE:
        assertStatesUnavailable(historicalStates.keySet());
        break;
    }
  }
}
