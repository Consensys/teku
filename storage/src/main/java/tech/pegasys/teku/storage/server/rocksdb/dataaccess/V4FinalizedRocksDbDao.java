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

package tech.pegasys.teku.storage.server.rocksdb.dataaccess;

import com.google.common.primitives.UnsignedLong;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.storage.server.rocksdb.core.ColumnEntry;
import tech.pegasys.teku.storage.server.rocksdb.core.RocksDbAccessor;
import tech.pegasys.teku.storage.server.rocksdb.schema.V4SchemaFinalized;

public class V4FinalizedRocksDbDao implements RocksDbFinalizedDao {
  private final RocksDbAccessor db;

  public V4FinalizedRocksDbDao(final RocksDbAccessor db) {
    this.db = db;
  }

  @Override
  public void close() throws Exception {
    db.close();
  }

  @Override
  public Optional<Bytes32> getFinalizedRootAtSlot(final UnsignedLong slot) {
    return db.get(V4SchemaFinalized.FINALIZED_ROOTS_BY_SLOT, slot);
  }

  @Override
  public Optional<Bytes32> getLatestFinalizedRootAtSlot(final UnsignedLong slot) {
    return db.getFloorEntry(V4SchemaFinalized.FINALIZED_ROOTS_BY_SLOT, slot)
        .map(ColumnEntry::getValue);
  }

  @Override
  public Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root) {
    return db.get(V4SchemaFinalized.FINALIZED_BLOCKS_BY_ROOT, root);
  }

  @Override
  public Optional<BeaconState> getFinalizedState(final Bytes32 root) {
    return db.get(V4SchemaFinalized.FINALIZED_STATES_BY_ROOT, root);
  }

  @Override
  public FinalizedUpdater finalizedUpdater() {
    return new V4FinalizedRocksDbDao.V4FinalizedUpdater(db);
  }

  private static class V4FinalizedUpdater implements FinalizedUpdater {
    private final RocksDbAccessor.RocksDbTransaction transaction;

    V4FinalizedUpdater(final RocksDbAccessor db) {
      this.transaction = db.startTransaction();
    }

    @Override
    public void addFinalizedBlock(final SignedBeaconBlock block) {
      final Bytes32 root = block.getRoot();
      transaction.put(V4SchemaFinalized.FINALIZED_ROOTS_BY_SLOT, block.getSlot(), root);
      transaction.put(V4SchemaFinalized.FINALIZED_BLOCKS_BY_ROOT, root, block);
    }

    @Override
    public void addFinalizedState(final Bytes32 blockRoot, final BeaconState state) {
      transaction.put(V4SchemaFinalized.FINALIZED_STATES_BY_ROOT, blockRoot, state);
    }

    @Override
    public void commit() {
      // Commit db updates
      transaction.commit();
      close();
    }

    @Override
    public void cancel() {
      transaction.rollback();
      close();
    }

    @Override
    public void close() {
      transaction.close();
    }
  }
}
