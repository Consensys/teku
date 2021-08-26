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
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedTrieState;

public class V4FinalizedStateTrieStorageLogic
    implements V4FinalizedStateStorageLogic<SchemaFinalizedTrieState> {

  @Override
  public Optional<BeaconState> getLatestAvailableFinalizedState(
      final KvStoreAccessor db, final SchemaFinalizedTrieState schema, final UInt64 maxSlot) {
    return Optional.empty();
  }

  @Override
  public FinalizedStateUpdater<SchemaFinalizedTrieState> updater() {
    return new StateTrieUpdater();
  }

  private static class StateTrieUpdater implements FinalizedStateUpdater<SchemaFinalizedTrieState> {

    @Override
    public void addFinalizedState(
        final KvStoreAccessor db,
        final KvStoreTransaction transaction,
        final SchemaFinalizedTrieState schema,
        final BeaconState state) {}
  }
}
