/*
 * Copyright 2021 ConsenSys AG.
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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.kvstore.ColumnEntry;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedSnapshotState;

public class V4FinalizedStateSnapshotStorageLogic
    implements V4FinalizedStateStorageLogic<SchemaFinalizedSnapshotState> {

  private final UInt64 stateStorageFrequency;

  public V4FinalizedStateSnapshotStorageLogic(final long stateStorageFrequency) {
    this.stateStorageFrequency = UInt64.valueOf(stateStorageFrequency);
  }

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(
      final KvStoreAccessor db, final SchemaFinalizedSnapshotState schema, final UInt64 maxSlot) {
    return db.getFloorEntry(schema.getColumnFinalizedStatesBySlot(), maxSlot)
        .map(ColumnEntry::getValue);
  }

  @Override
  public FinalizedStateUpdater<SchemaFinalizedSnapshotState> updater() {
    return new FinalizedStateSnapshotUpdater(stateStorageFrequency);
  }

  private static class FinalizedStateSnapshotUpdater
      implements V4FinalizedStateStorageLogic.FinalizedStateUpdater<SchemaFinalizedSnapshotState> {

    private final UInt64 stateStorageFrequency;
    private Optional<UInt64> lastStateStoredSlot = Optional.empty();
    private boolean loadedLastStoreState = false;

    private FinalizedStateSnapshotUpdater(final UInt64 stateStorageFrequency) {
      this.stateStorageFrequency = stateStorageFrequency;
    }

    @Override
    public void addFinalizedState(
        final KvStoreAccessor db,
        final KvStoreTransaction transaction,
        final SchemaFinalizedSnapshotState schema,
        final BeaconState state) {
      if (!loadedLastStoreState) {
        lastStateStoredSlot = db.getLastKey(schema.getColumnFinalizedStatesBySlot());
        loadedLastStoreState = true;
      }
      if (lastStateStoredSlot.isPresent()) {
        UInt64 nextStorageSlot = lastStateStoredSlot.get().plus(stateStorageFrequency);
        if (state.getSlot().compareTo(nextStorageSlot) >= 0) {
          addFinalizedState(transaction, schema, state);
        }
      } else {
        addFinalizedState(transaction, schema, state);
      }
    }

    @Override
    public void commit() {}

    private void addFinalizedState(
        final KvStoreTransaction transaction,
        final SchemaFinalizedSnapshotState schema,
        final BeaconState state) {
      transaction.put(schema.getColumnFinalizedStatesBySlot(), state.getSlot(), state);
      lastStateStoredSlot = Optional.of(state.getSlot());
    }
  }
}
