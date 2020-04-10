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

package tech.pegasys.artemis.storage.server.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.primitives.UnsignedLong;
import java.io.File;
import org.junit.jupiter.api.Test;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.storage.Store.Transaction;
import tech.pegasys.artemis.storage.server.AbstractStorageBackedDatabaseTest;
import tech.pegasys.artemis.storage.server.Database;
import tech.pegasys.artemis.storage.server.StateStorageMode;

public class RocksDbDatabaseTest extends AbstractStorageBackedDatabaseTest {

  @Override
  protected Database createDatabase(final File tempDir, final StateStorageMode storageMode) {
    final RocksDbConfiguration config = RocksDbConfiguration.withDataDirectory(tempDir.toPath());
    return RocksDbDatabase.createOnDisk(config, storageMode);
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsModified_setGenesis() throws Exception {
    database.close();
    assertThatThrownBy(() -> database.storeGenesis(store))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsModified_update() throws Exception {
    database.storeGenesis(store);
    database.close();

    final Checkpoint newValue = checkpoint3;
    // Sanity check
    assertThat(store.getFinalizedCheckpoint()).isNotEqualTo(checkpoint3);
    final Transaction transaction = store.startTransaction(storageUpdateChannel);
    transaction.setFinalizedCheckpoint(newValue);

    assertThatThrownBy(transaction::commit).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsRead_createMemoryStore() throws Exception {
    database.storeGenesis(store);
    database.close();

    assertThatThrownBy(database::createMemoryStore).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsRead_getFinalizedRootAtSlot() throws Exception {
    database.storeGenesis(store);
    database.close();

    assertThatThrownBy(() -> database.getFinalizedRootAtSlot(UnsignedLong.ONE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsRead_getLatestFinalizedRootAtSlot() throws Exception {
    database.storeGenesis(store);
    database.close();

    assertThatThrownBy(() -> database.getLatestFinalizedRootAtSlot(UnsignedLong.ONE))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsRead_getSignedBlock() throws Exception {
    database.storeGenesis(store);
    database.close();

    assertThatThrownBy(() -> database.getSignedBlock(genesisCheckpoint.getRoot()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void shouldThrowIfClosedDatabaseIsRead_getState() throws Exception {
    database.storeGenesis(store);
    database.close();

    assertThatThrownBy(() -> database.getState(genesisCheckpoint.getRoot()))
        .isInstanceOf(IllegalStateException.class);
  }
}
