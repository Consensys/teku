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
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.UINT64_SERIALIZER;

import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.datastructures.state.BeaconState;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class V4SchemaFinalized implements SchemaFinalized {
  public static final SchemaFinalized INSTANCE = new V4SchemaFinalized();

  private static final RocksDbColumn<Bytes32, UInt64> SLOTS_BY_FINALIZED_ROOT =
      RocksDbColumn.create(1, BYTES32_SERIALIZER, UINT64_SERIALIZER);
  private static final RocksDbColumn<UInt64, SignedBeaconBlock> FINALIZED_BLOCKS_BY_SLOT =
      RocksDbColumn.create(2, UINT64_SERIALIZER, SIGNED_BLOCK_SERIALIZER);
  private static final RocksDbColumn<UInt64, BeaconState> FINALIZED_STATES_BY_SLOT =
      RocksDbColumn.create(3, UINT64_SERIALIZER, STATE_SERIALIZER);
  private static final RocksDbColumn<Bytes32, UInt64> SLOTS_BY_FINALIZED_STATE_ROOT =
      RocksDbColumn.create(4, BYTES32_SERIALIZER, UINT64_SERIALIZER);
  private static final List<RocksDbColumn<?, ?>> ALL_COLUMNS =
      List.of(
          SLOTS_BY_FINALIZED_ROOT,
          FINALIZED_BLOCKS_BY_SLOT,
          FINALIZED_STATES_BY_SLOT,
          SLOTS_BY_FINALIZED_STATE_ROOT);

  private V4SchemaFinalized() {}

  @Override
  public RocksDbColumn<Bytes32, UInt64> getColumnSlotsByFinalizedRoot() {
    return SLOTS_BY_FINALIZED_ROOT;
  }

  @Override
  public RocksDbColumn<UInt64, SignedBeaconBlock> getColumnFinalizedBlocksBySlot() {
    return FINALIZED_BLOCKS_BY_SLOT;
  }

  @Override
  public RocksDbColumn<UInt64, BeaconState> getColumnFinalizedStatesBySlot() {
    return FINALIZED_STATES_BY_SLOT;
  }

  @Override
  public RocksDbColumn<Bytes32, UInt64> getColumnSlotsByFinalizedStateRoot() {
    return SLOTS_BY_FINALIZED_STATE_ROOT;
  }

  @Override
  public List<RocksDbColumn<?, ?>> getAllColumns() {
    return ALL_COLUMNS;
  }

  @Override
  public List<RocksDbVariable<?>> getAllVariables() {
    return Collections.emptyList();
  }
}
