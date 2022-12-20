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
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.COMPRESSED_BRANCH_INFO_KV_STORE_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.SLOT_AND_BLOCK_ROOT_KEY_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.UINT64_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.VOID_SERIALIZER;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer;

public class V6SchemaCombinedTreeState extends V6SchemaCombined implements SchemaCombinedTreeState {

  private final KvStoreColumn<Bytes32, UInt64> slotsByFinalizedRoot;
  private final KvStoreColumn<UInt64, SignedBeaconBlock> finalizedBlocksBySlot;
  private final KvStoreColumn<Bytes32, SignedBeaconBlock> nonCanonicalBlocksByRoot;
  private final KvStoreColumn<Bytes32, UInt64> slotsByFinalizedStateRoot;
  private final KvStoreColumn<UInt64, Set<Bytes32>> nonCanonicalBlockRootsBySlot;
  private final KvStoreColumn<UInt64, Bytes32> finalizedStateRootsBySlot;
  private final KvStoreColumn<Bytes32, Bytes> finalizedStateTreeLeavesByRoot;
  private final KvStoreColumn<Bytes32, CompressedBranchInfo> finalizedStateTreeBranchesByRoot;

  private final KvStoreColumn<SlotAndBlockRoot, Bytes> blobsSidecarBySlotAndBlockRoot;
  private final KvStoreColumn<SlotAndBlockRoot, Void> unconfirmedBlobsSidecarBySlotAndBlockRoot;
  private final List<Bytes> deletedColumnIds;

  public V6SchemaCombinedTreeState(final Spec spec) {
    super(spec, V6_FINALIZED_OFFSET);
    slotsByFinalizedRoot =
        KvStoreColumn.create(V6_FINALIZED_OFFSET + 1, BYTES32_SERIALIZER, UINT64_SERIALIZER);
    slotsByFinalizedStateRoot =
        KvStoreColumn.create(V6_FINALIZED_OFFSET + 2, BYTES32_SERIALIZER, UINT64_SERIALIZER);
    nonCanonicalBlockRootsBySlot =
        KvStoreColumn.create(V6_FINALIZED_OFFSET + 3, UINT64_SERIALIZER, BLOCK_ROOTS_SERIALIZER);
    finalizedStateRootsBySlot =
        KvStoreColumn.create(V6_FINALIZED_OFFSET + 4, UINT64_SERIALIZER, BYTES32_SERIALIZER);
    finalizedStateTreeLeavesByRoot =
        KvStoreColumn.create(V6_FINALIZED_OFFSET + 5, BYTES32_SERIALIZER, BYTES_SERIALIZER);
    finalizedStateTreeBranchesByRoot =
        KvStoreColumn.create(
            V6_FINALIZED_OFFSET + 6,
            BYTES32_SERIALIZER,
            COMPRESSED_BRANCH_INFO_KV_STORE_SERIALIZER);
    finalizedBlocksBySlot =
        KvStoreColumn.create(
            V6_FINALIZED_OFFSET + 7,
            UINT64_SERIALIZER,
            KvStoreSerializer.createSignedBlockSerializer(spec));
    nonCanonicalBlocksByRoot =
        KvStoreColumn.create(
            V6_FINALIZED_OFFSET + 8,
            BYTES32_SERIALIZER,
            KvStoreSerializer.createSignedBlockSerializer(spec));

    blobsSidecarBySlotAndBlockRoot =
        KvStoreColumn.create(
            finalizedOffset + 12, SLOT_AND_BLOCK_ROOT_KEY_SERIALIZER, BYTES_SERIALIZER);
    unconfirmedBlobsSidecarBySlotAndBlockRoot =
        KvStoreColumn.create(
            finalizedOffset + 13, SLOT_AND_BLOCK_ROOT_KEY_SERIALIZER, VOID_SERIALIZER);
    deletedColumnIds =
        List.of(
            asColumnId(finalizedOffset + 9),
            asColumnId(finalizedOffset + 10),
            asColumnId(finalizedOffset + 11));
  }

  @Override
  public KvStoreColumn<UInt64, Bytes32> getColumnFinalizedStateRootsBySlot() {
    return finalizedStateRootsBySlot;
  }

  @Override
  public KvStoreColumn<Bytes32, Bytes> getColumnFinalizedStateMerkleTreeLeaves() {
    return finalizedStateTreeLeavesByRoot;
  }

  @Override
  public KvStoreColumn<Bytes32, CompressedBranchInfo> getColumnFinalizedStateMerkleTreeBranches() {
    return finalizedStateTreeBranchesByRoot;
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
  public KvStoreColumn<SlotAndBlockRoot, Bytes> getColumnBlobsSidecarBySlotAndBlockRoot() {
    return blobsSidecarBySlotAndBlockRoot;
  }

  @Override
  public KvStoreColumn<SlotAndBlockRoot, Void>
      getColumnUnconfirmedBlobsSidecarBySlotAndBlockRoot() {
    return unconfirmedBlobsSidecarBySlotAndBlockRoot;
  }

  @Override
  public Map<String, KvStoreVariable<?>> getVariableMap() {
    return Map.of(
        "GENESIS_TIME", getVariableGenesisTime(),
        "JUSTIFIED_CHECKPOINT", getVariableJustifiedCheckpoint(),
        "BEST_JUSTIFIED_CHECKPOINT", getVariableBestJustifiedCheckpoint(),
        "FINALIZED_CHECKPOINT", getVariableFinalizedCheckpoint(),
        "LATEST_FINALIZED_STATE", getVariableLatestFinalizedState(),
        "MIN_GENESIS_TIME_BLOCK", getVariableMinGenesisTimeBlock(),
        "WEAK_SUBJECTIVITY_CHECKPOINT", getVariableWeakSubjectivityCheckpoint(),
        "ANCHOR_CHECKPOINT", getVariableAnchorCheckpoint(),
        "OPTIMISTIC_TRANSITION_BLOCK_SLOT", getOptimisticTransitionBlockSlot(),
        "FINALIZED_DEPOSIT_SNAPSHOT", getVariableFinalizedDepositSnapshot());
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
        .put("FINALIZED_STATE_ROOTS_BY_SLOT", getColumnFinalizedStateRootsBySlot())
        .put("FINALIZED_STATE_TREE_LEAVES", getColumnFinalizedStateMerkleTreeLeaves())
        .put("FINALIZED_STATE_TREE_BRANCHES", getColumnFinalizedStateMerkleTreeBranches())
        .put("SLOTS_BY_FINALIZED_STATE_ROOT", getColumnSlotsByFinalizedStateRoot())
        .put("NON_CANONICAL_BLOCKS_BY_ROOT", getColumnNonCanonicalBlocksByRoot())
        .put("NON_CANONICAL_BLOCK_ROOTS_BY_SLOT", getColumnNonCanonicalRootsBySlot())
        .put("BLOBS_SIDECAR_BY_SLOT_AND_BLOCK_ROOT", getColumnBlobsSidecarBySlotAndBlockRoot())
        .put(
            "UNCONFIRMED_BLOBS_SIDECAR_BY_SLOT_AND_BLOCK_ROOT",
            getColumnUnconfirmedBlobsSidecarBySlotAndBlockRoot())
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
