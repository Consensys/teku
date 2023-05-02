/*
 * Copyright ConsenSys Software Inc., 2020
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

import static tech.pegasys.teku.storage.server.kvstore.schema.KvStoreColumn.asColumnId;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.BLOCK_ROOTS_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.BYTES32_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.BYTES_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.SLOT_AND_BLOCK_ROOT_AND_BLOB_INDEX_KEY_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.UINT64_SERIALIZER;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer;

public class V6SchemaCombinedSnapshot extends V6SchemaCombined
    implements SchemaCombinedSnapshotState {

  private final KvStoreColumn<Bytes32, UInt64> slotsByFinalizedRoot;
  private final KvStoreColumn<UInt64, SignedBeaconBlock> finalizedBlocksBySlot;
  private final KvStoreColumn<Bytes32, SignedBeaconBlock> nonCanonicalBlocksByRoot;
  private final KvStoreColumn<Bytes32, UInt64> slotsByFinalizedStateRoot;
  private final KvStoreColumn<UInt64, Set<Bytes32>> nonCanonicalBlockRootsBySlot;
  private final KvStoreColumn<UInt64, BeaconState> finalizedStatesBySlot;

  private final KvStoreColumn<SlotAndBlockRootAndBlobIndex, Bytes> blobSidecarBySlotRootBlobIndex;
  private final List<Bytes> deletedColumnIds;

  private V6SchemaCombinedSnapshot(final Spec spec, final int finalizedOffset) {
    super(spec, finalizedOffset);
    slotsByFinalizedRoot =
        KvStoreColumn.create(finalizedOffset + 1, BYTES32_SERIALIZER, UINT64_SERIALIZER);
    finalizedBlocksBySlot =
        KvStoreColumn.create(
            finalizedOffset + 2,
            UINT64_SERIALIZER,
            KvStoreSerializer.createSignedBlockSerializer(spec));
    finalizedStatesBySlot =
        KvStoreColumn.create(
            finalizedOffset + 3, UINT64_SERIALIZER, KvStoreSerializer.createStateSerializer(spec));
    slotsByFinalizedStateRoot =
        KvStoreColumn.create(finalizedOffset + 4, BYTES32_SERIALIZER, UINT64_SERIALIZER);
    nonCanonicalBlocksByRoot =
        KvStoreColumn.create(
            finalizedOffset + 5,
            BYTES32_SERIALIZER,
            KvStoreSerializer.createSignedBlockSerializer(spec));
    nonCanonicalBlockRootsBySlot =
        KvStoreColumn.create(finalizedOffset + 6, UINT64_SERIALIZER, BLOCK_ROOTS_SERIALIZER);
    blobSidecarBySlotRootBlobIndex =
        KvStoreColumn.create(
            finalizedOffset + 12,
            SLOT_AND_BLOCK_ROOT_AND_BLOB_INDEX_KEY_SERIALIZER,
            BYTES_SERIALIZER);

    deletedColumnIds =
        List.of(
            asColumnId(finalizedOffset + 7),
            asColumnId(finalizedOffset + 8),
            asColumnId(finalizedOffset + 9),
            asColumnId(finalizedOffset + 10),
            asColumnId(finalizedOffset + 11));
  }

  public static V6SchemaCombinedSnapshot createV4(final Spec spec) {
    return new V6SchemaCombinedSnapshot(spec, V4_FINALIZED_OFFSET);
  }

  public static V6SchemaCombinedSnapshot createV6(final Spec spec) {
    return new V6SchemaCombinedSnapshot(spec, V6_FINALIZED_OFFSET);
  }

  @Override
  public KvStoreColumn<UInt64, BeaconState> getColumnFinalizedStatesBySlot() {
    return finalizedStatesBySlot;
  }

  @Override
  public KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedRoot() {
    return slotsByFinalizedRoot;
  }

  @Override
  public KvStoreColumn<UInt64, SignedBeaconBlock> getColumnFinalizedBlocksBySlot() {
    return finalizedBlocksBySlot;
  }

  @Override
  public KvStoreColumn<Bytes32, UInt64> getColumnSlotsByFinalizedStateRoot() {
    return slotsByFinalizedStateRoot;
  }

  @Override
  public KvStoreColumn<Bytes32, SignedBeaconBlock> getColumnNonCanonicalBlocksByRoot() {
    return nonCanonicalBlocksByRoot;
  }

  @Override
  public KvStoreColumn<UInt64, Set<Bytes32>> getColumnNonCanonicalRootsBySlot() {
    return nonCanonicalBlockRootsBySlot;
  }

  @Override
  public KvStoreColumn<SlotAndBlockRootAndBlobIndex, Bytes>
      getColumnBlobSidecarBySlotRootBlobIndex() {
    return blobSidecarBySlotRootBlobIndex;
  }

  @Override
  public Map<String, KvStoreColumn<?, ?>> getColumnMap() {
    return ImmutableMap.<String, KvStoreColumn<?, ?>>builder()
        .put("HOT_BLOCKS_BY_ROOT", getColumnHotBlocksByRoot())
        .put("CHECKPOINT_STATES", getColumnCheckpointStates())
        .put("VOTES", getColumnVotes())
        .put("DEPOSITS_FROM_BLOCK_EVENTS", getColumnDepositsFromBlockEvents())
        .put("STATE_ROOT_TO_SLOT_AND_BLOCK_ROOT", getColumnStateRootToSlotAndBlockRoot())
        .put("HOT_STATES_BY_ROOT", getColumnHotStatesByRoot())
        .put("HOT_BLOCK_CHECKPOINT_EPOCHS_BY_ROOT", getColumnHotBlockCheckpointEpochsByRoot())
        .put("SLOTS_BY_FINALIZED_ROOT", getColumnSlotsByFinalizedRoot())
        .put("FINALIZED_BLOCKS_BY_SLOT", getColumnFinalizedBlocksBySlot())
        .put("FINALIZED_STATES_BY_SLOT", getColumnFinalizedStatesBySlot())
        .put("SLOTS_BY_FINALIZED_STATE_ROOT", getColumnSlotsByFinalizedStateRoot())
        .put("NON_CANONICAL_BLOCKS_BY_ROOT", getColumnNonCanonicalBlocksByRoot())
        .put("NON_CANONICAL_BLOCK_ROOTS_BY_SLOT", getColumnNonCanonicalRootsBySlot())
        .put(
            "BLOB_SIDECAR_BY_SLOT_AND_BLOCK_ROOT_AND_BLOB_INDEX",
            getColumnBlobSidecarBySlotRootBlobIndex())
        .build();
  }

  @Override
  public Collection<KvStoreColumn<?, ?>> getAllColumns() {
    return getColumnMap().values();
  }

  @Override
  public Collection<KvStoreVariable<?>> getAllVariables() {
    return getVariableMap().values();
  }

  @Override
  public Collection<Bytes> getDeletedColumnIds() {
    return deletedColumnIds;
  }
}
