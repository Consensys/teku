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
import static tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer.UINT64_SERIALIZER;

import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.rocksdb.serialization.RocksDbSerializer;

/**
 * The same as {@link V4SchemaFinalized} but with other column ids which are distinct from {@link
 * V4SchemaHot}
 */
public class V6SchemaFinalized implements SchemaFinalized {
  // column ids should be distinct across different DAOs to make possible using
  // schemes both for a single and separated DBs
  private static final int ID_OFFSET = 128;

  private static final RocksDbColumn<Bytes32, UInt64> SLOTS_BY_FINALIZED_ROOT =
      RocksDbColumn.create(ID_OFFSET + 1, BYTES32_SERIALIZER, UINT64_SERIALIZER);
  private final RocksDbColumn<UInt64, SignedBeaconBlock> finalizedBlocksBySlot;
  private final RocksDbColumn<UInt64, BeaconState> finalizedStatesBySlot;
  private static final RocksDbColumn<Bytes32, UInt64> SLOTS_BY_FINALIZED_STATE_ROOT =
      RocksDbColumn.create(ID_OFFSET + 4, BYTES32_SERIALIZER, UINT64_SERIALIZER);

  private V6SchemaFinalized(final Spec spec) {
    finalizedBlocksBySlot =
        RocksDbColumn.create(
            ID_OFFSET + 2, UINT64_SERIALIZER, RocksDbSerializer.createSignedBlockSerializer(spec));
    finalizedStatesBySlot =
        RocksDbColumn.create(
            ID_OFFSET + 3, UINT64_SERIALIZER, RocksDbSerializer.createStateSerializer(spec));
  }

  public static SchemaFinalized create(final Spec spec) {
    return new V6SchemaFinalized(spec);
  }

  @Override
  public RocksDbColumn<Bytes32, UInt64> getColumnSlotsByFinalizedRoot() {
    return SLOTS_BY_FINALIZED_ROOT;
  }

  @Override
  public RocksDbColumn<UInt64, SignedBeaconBlock> getColumnFinalizedBlocksBySlot() {
    return finalizedBlocksBySlot;
  }

  @Override
  public RocksDbColumn<UInt64, BeaconState> getColumnFinalizedStatesBySlot() {
    return finalizedStatesBySlot;
  }

  @Override
  public RocksDbColumn<Bytes32, UInt64> getColumnSlotsByFinalizedStateRoot() {
    return SLOTS_BY_FINALIZED_STATE_ROOT;
  }

  @Override
  public List<RocksDbColumn<?, ?>> getAllColumns() {
    return List.of(
        SLOTS_BY_FINALIZED_ROOT,
        finalizedBlocksBySlot,
        finalizedStatesBySlot,
        SLOTS_BY_FINALIZED_STATE_ROOT);
  }

  @Override
  public List<RocksDbVariable<?>> getAllVariables() {
    return Collections.emptyList();
  }
}
