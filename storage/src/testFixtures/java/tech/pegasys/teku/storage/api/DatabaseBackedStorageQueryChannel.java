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

package tech.pegasys.teku.storage.api;

import com.google.common.primitives.UnsignedLong;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.storage.server.Database;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.util.async.SafeFuture;

public class DatabaseBackedStorageQueryChannel implements StorageQueryChannel {
  private final Database database;

  public DatabaseBackedStorageQueryChannel(final Database database) {
    this.database = database;
  }

  @Override
  public SafeFuture<Optional<StoreBuilder>> onStoreRequest() {
    return SafeFuture.completedFuture(database.createMemoryStore());
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getFinalizedBlockAtSlot(final UnsignedLong slot) {
    return SafeFuture.of(() -> database.getFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getLatestFinalizedBlockAtSlot(
      final UnsignedLong slot) {
    return SafeFuture.of(() -> database.getLatestFinalizedBlockAtSlot(slot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> getBlockByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.of(() -> database.getSignedBlock(blockRoot));
  }

  @Override
  public SafeFuture<Map<Bytes32, SignedBeaconBlock>> getHotBlocksByRoot(
      final Set<Bytes32> blockRoots) {
    return SafeFuture.of(() -> database.getHotBlocks(blockRoots));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getLatestFinalizedStateAtSlot(final UnsignedLong slot) {
    return SafeFuture.of(() -> getLatestFinalizedStateAtSlotSync(slot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> getFinalizedStateByBlockRoot(final Bytes32 blockRoot) {
    return SafeFuture.of(
        () ->
            database
                .getSlotForFinalizedBlockRoot(blockRoot)
                .flatMap(this::getLatestFinalizedStateAtSlotSync));
  }

  private Optional<BeaconState> getLatestFinalizedStateAtSlotSync(UnsignedLong slot) {
    return database.getLatestAvailableFinalizedState(slot);
  }
}
