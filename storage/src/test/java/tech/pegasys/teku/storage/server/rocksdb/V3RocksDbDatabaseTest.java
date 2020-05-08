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
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.core.ChainBuilder;
import tech.pegasys.teku.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.Store;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.util.config.StateStorageMode;

public class V3RocksDbDatabaseTest extends AbstractRocksDbDatabaseTest {

  @Override
  protected Database createDatabase(final File tempDir, final StateStorageMode storageMode) {
    final RocksDbConfiguration config = RocksDbConfiguration.withDataDirectory(tempDir.toPath());
    return RocksDbDatabase.createV3(config, storageMode);
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

  private void testShouldHandleRestartWithUnrecoverableForkBlocks(
      @TempDir final Path tempDir, final StateStorageMode storageMode) throws Exception {
    // Setup chains
    // Both chains share block up to slot 3
    final ChainBuilder primaryChain = ChainBuilder.create(VALIDATOR_KEYS);
    final SignedBlockAndState genesis = primaryChain.generateGenesis();
    primaryChain.generateBlocksUpToSlot(3);
    final ChainBuilder forkChain = primaryChain.fork();
    // Fork chain's next block is at 6
    forkChain.generateBlockAtSlot(6);
    forkChain.generateBlocksUpToSlot(9);
    // Primary chain's next block is at 7
    primaryChain.generateBlockAtSlot(7);
    primaryChain.generateBlocksUpToSlot(9);

    // Setup database
    database = setupDatabase(tempDir.toFile(), storageMode);
    store = Store.getForkChoiceStore(genesis.getState());
    database.storeGenesis(store);

    final Set<SignedBlockAndState> allBlocksAndStates =
        Streams.concat(primaryChain.streamBlocksAndStates(), forkChain.streamBlocksAndStates())
            .collect(Collectors.toSet());

    // Finalize at block 7, making fork blocks from 7-9 unrecoverable
    add(allBlocksAndStates);
    final Checkpoint finalizedCheckpoint = getCheckpointForBlock(primaryChain.getBlockAtSlot(7));
    finalizeCheckpoint(finalizedCheckpoint);

    // Close database and rebuild from disk
    database.close();
    database = setupDatabase(tempDir.toFile(), storageMode);

    // We should be able to access primary hot blocks and state
    final List<SignedBlockAndState> expectedHotBlocksAndStates =
        primaryChain.streamBlocksAndStates(7, 9).collect(toList());
    assertHotBlocksAndStatesInclude(expectedHotBlocksAndStates);

    // Fork states should be unavailable
    final List<Bytes32> unavailableBlockRoots =
        forkChain
            .streamBlocksAndStates(7, 9)
            .map(SignedBlockAndState::getRoot)
            .collect(Collectors.toList());
    assertStatesUnavailable(unavailableBlockRoots);
    assertBlocksUnavailable(unavailableBlockRoots);
  }
}
