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
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalized;

public interface V4FinalizedStateStorageLogic<S extends SchemaFinalized> {
  Optional<BeaconState> getLatestAvailableFinalizedState(
      KvStoreAccessor db, S schema, UInt64 maxSlot);

  FinalizedStateUpdater<S> updater();

  interface FinalizedStateUpdater<S extends SchemaFinalized> {
    void addFinalizedState(
        KvStoreAccessor db, KvStoreTransaction transaction, S schema, BeaconState state);

    void commit();
  }
}
