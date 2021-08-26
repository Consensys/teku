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

package tech.pegasys.teku.storage.server.kvstore.schema;

import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.BLOCK_ROOTS_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.BYTES32_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.UINT64_SERIALIZER;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer;

public class V4SchemaFinalized implements SchemaFinalizedSnapshotState {
  private static final KvStoreColumn<Bytes32, UInt64> SLOTS_BY_FINALIZED_ROOT =
      KvStoreColumn.create(1, BYTES32_SERIALIZER, UINT64_SERIALIZER);
  private final KvStoreColumn<UInt64, SignedBeaconBlock> finalizedBlocksBySlot;
  private final KvStoreColumn<UInt64, BeaconState> finalizedStatesBySlot;
  private final KvStoreColumn<Bytes32, SignedBeaconBlock> nonCanonicalBlocksByRoot;
  private static final KvStoreColumn<Bytes32, UInt64> SLOTS_BY_FINALIZED_STATE_ROOT =
      KvStoreColumn.create(4, BYTES32_SERIALIZER, UINT64_SERIALIZER);
  private static final KvStoreColumn<UInt64, Set<Bytes32>> NON_CANONICAL_BLOCK_ROOTS_BY_SLOT =
      KvStoreColumn.create(6, UINT64_SERIALIZER, BLOCK_ROOTS_SERIALIZER);

  public V4SchemaFinalized(final Spec spec) {
    finalizedBlocksBySlot =
        KvStoreColumn.create(
            2, UINT64_SERIALIZER, KvStoreSerializer.createSignedBlockSerializer(spec));
    this.finalizedStatesBySlot =
        KvStoreColumn.create(3, UINT64_SERIALIZER, KvStoreSerializer.createStateSerializer(spec));
    nonCanonicalBlocksByRoot =
        KvStoreColumn.create(
            5, BYTES32_SERIALIZER, KvStoreSerializer.createSignedBlockSerializer(spec));
  }

  @Override
  public KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedRoot() {
    return SLOTS_BY_FINALIZED_ROOT;
  }

  @Override
  public KvStoreColumn<UInt64, SignedBeaconBlock> getColumnFinalizedBlocksBySlot() {
    return finalizedBlocksBySlot;
  }

  @Override
  public KvStoreColumn<UInt64, BeaconState> getColumnFinalizedStatesBySlot() {
    return finalizedStatesBySlot;
  }

  @Override
  public KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedStateRoot() {
    return SLOTS_BY_FINALIZED_STATE_ROOT;
  }

  @Override
  public KvStoreColumn<Bytes32, SignedBeaconBlock> getColumnNonCanonicalBlocksByRoot() {
    return nonCanonicalBlocksByRoot;
  }

  @Override
  public KvStoreColumn<UInt64, Set<Bytes32>> getColumnNonCanonicalRootsBySlot() {
    return NON_CANONICAL_BLOCK_ROOTS_BY_SLOT;
  }

  @Override
  public List<KvStoreVariable<?>> getAllVariables() {
    return Collections.emptyList();
  }
}
