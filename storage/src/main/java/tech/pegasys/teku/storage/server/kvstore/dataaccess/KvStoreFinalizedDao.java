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

package tech.pegasys.teku.storage.server.kvstore.dataaccess;

import com.google.errorprone.annotations.MustBeClosed;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/**
 * Provides an abstract "data access object" interface for working with finalized data from the
 * underlying database.
 */
public interface KvStoreFinalizedDao extends AutoCloseable {

  Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root);

  FinalizedUpdater finalizedUpdater();

  Optional<SignedBeaconBlock> getFinalizedBlockAtSlot(UInt64 slot);

  Optional<UInt64> getEarliestFinalizedBlockSlot();

  Optional<SignedBeaconBlock> getEarliestFinalizedBlock();

  Optional<SignedBeaconBlock> getLatestFinalizedBlockAtSlot(UInt64 slot);

  Set<SignedBeaconBlock> getNonCanonicalBlocksAtSlot(UInt64 slot);

  Optional<BeaconState> getLatestAvailableFinalizedState(UInt64 maxSlot);

  @MustBeClosed
  Stream<SignedBeaconBlock> streamFinalizedBlocks(UInt64 startSlot, UInt64 endSlot);

  Optional<UInt64> getSlotForFinalizedBlockRoot(Bytes32 blockRoot);

  Optional<UInt64> getSlotForFinalizedStateRoot(Bytes32 stateRoot);

  Optional<SlotAndBlockRoot> getSlotAndBlockRootForFinalizedStateRoot(Bytes32 stateRoot);

  Optional<? extends SignedBeaconBlock> getNonCanonicalBlock(Bytes32 root);

  void ingest(KvStoreFinalizedDao finalizedDao, final int batchSize, final Consumer<String> logger);

  interface FinalizedUpdater extends AutoCloseable {

    void addFinalizedBlock(final SignedBeaconBlock block);

    void addNonCanonicalBlock(final SignedBeaconBlock block);

    void addNonCanonicalRootAtSlot(final UInt64 slot, final Set<Bytes32> blockRoots);

    void addFinalizedState(final Bytes32 blockRoot, final BeaconState state);

    void addFinalizedStateRoot(final Bytes32 stateRoot, final UInt64 slot);

    void commit();

    void cancel();

    @Override
    void close();
  }
}
