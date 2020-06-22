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

/**
 * Provides an abstract "data access object" interface for working with finalized data from the
 * underlying database.
 */
public interface RocksDbFinalizedDao extends AutoCloseable {

  Optional<Bytes32> getFinalizedRootAtSlot(final UnsignedLong slot);

  Optional<Bytes32> getLatestFinalizedRootAtSlot(final UnsignedLong slot);

  Optional<SignedBeaconBlock> getFinalizedBlock(final Bytes32 root);

  Optional<BeaconState> getFinalizedState(final Bytes32 root);

  FinalizedUpdater finalizedUpdater();

  interface FinalizedUpdater extends AutoCloseable {

    void addFinalizedBlock(final SignedBeaconBlock block);

    void addFinalizedState(final Bytes32 blockRoot, final BeaconState state);

    void commit();

    void cancel();

    @Override
    void close();
  }
}
