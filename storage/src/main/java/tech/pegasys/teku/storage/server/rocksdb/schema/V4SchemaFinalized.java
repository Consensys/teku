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

package tech.pegasys.teku.storage.server.rocksdb.schema;

import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.BYTES32_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.SIGNED_BLOCK_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.STATE_SERIALIZER;
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.UNSIGNED_LONG_SERIALIZER;

import com.google.common.primitives.UnsignedLong;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;

public interface V4SchemaFinalized extends Schema {
  RocksDbColumn<UnsignedLong, Bytes32> FINALIZED_ROOTS_BY_SLOT =
      RocksDbColumn.create(1, UNSIGNED_LONG_SERIALIZER, BYTES32_SERIALIZER);
  RocksDbColumn<Bytes32, SignedBeaconBlock> FINALIZED_BLOCKS_BY_ROOT =
      RocksDbColumn.create(2, BYTES32_SERIALIZER, SIGNED_BLOCK_SERIALIZER);
  RocksDbColumn<Bytes32, BeaconState> FINALIZED_STATES_BY_ROOT =
      RocksDbColumn.create(3, BYTES32_SERIALIZER, STATE_SERIALIZER);
}
