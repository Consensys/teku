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

package tech.pegasys.teku.storage.server.kvstore;

import java.io.File;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import tech.pegasys.teku.storage.server.DatabaseVersion;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.StoreConfig;

public class InMemoryV4KvStoreBlindedBlocksDatabaseTest
    extends AbstractKvStoreDatabaseWithHotStatesTest {
  @Override
  protected StorageSystem createStorageSystem(
      final File tempDir,
      final StateStorageMode storageMode,
      final StoreConfig storeConfig,
      final boolean storeNonCanonicalBlocks) {
    return InMemoryStorageSystemBuilder.create()
        .specProvider(spec)
        .version(DatabaseVersion.V4)
        .storageMode(storageMode)
        .stateStorageFrequency(1L)
        .storeConfig(storeConfig)
        .storeNonCanonicalBlocks(storeNonCanonicalBlocks)
        .storeBlockExecutionPayloadSeparately(true)
        .build();
  }

  @Test
  @Override
  public void testShouldRecordFinalizedBlocksAndStatesInBatchUpdate() {
    // FIXME: It's currently assumed that we'll commit the block as hot first before finalizing
    // In real usage that always happens but previously we've designed the transaction system
    // to handle batch commits. It's probably worth keeping this ability but we haven't yet
    throw new TestAbortedException("Batch updates not supported");
  }
}
