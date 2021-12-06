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
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.BYTES_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.COMPRESSED_BRANCH_INFO_KV_STORE_SERIALIZER;
import static tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer.UINT64_SERIALIZER;

import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.storage.server.kvstore.serialization.KvStoreSerializer;

/**
 * The same as {@link V4SchemaFinalized} but with other column ids which are distinct from {@link
 * V4SchemaHot}
 */
public class V6TreeSchemaFinalized implements SchemaFinalizedTreeState {
  // column ids should be distinct across different DAOs to make possible using
  // schemes both for a single and separated DBs
  private static final int ID_OFFSET = 128;

  private static final KvStoreColumn<Bytes32, UInt64> SLOTS_BY_FINALIZED_ROOT =
      KvStoreColumn.create(ID_OFFSET + 1, BYTES32_SERIALIZER, UINT64_SERIALIZER);
  private static final KvStoreColumn<Bytes32, UInt64> SLOTS_BY_FINALIZED_STATE_ROOT =
      KvStoreColumn.create(ID_OFFSET + 2, BYTES32_SERIALIZER, UINT64_SERIALIZER);
  private static final KvStoreColumn<UInt64, Set<Bytes32>> NON_CANONICAL_BLOCK_ROOTS_BY_SLOT =
      KvStoreColumn.create(ID_OFFSET + 3, UINT64_SERIALIZER, BLOCK_ROOTS_SERIALIZER);
  private static final KvStoreColumn<UInt64, Bytes32> FINALIZED_STATE_ROOTS_BY_SLOT =
      KvStoreColumn.create(ID_OFFSET + 4, UINT64_SERIALIZER, BYTES32_SERIALIZER);
  private static final KvStoreColumn<Bytes32, Bytes> FINALIZED_STATE_TREE_LEAVES_BY_ROOT =
      KvStoreColumn.create(ID_OFFSET + 5, BYTES32_SERIALIZER, BYTES_SERIALIZER);
  private static final KvStoreColumn<Bytes32, CompressedBranchInfo>
      FINALIZED_STATE_TREE_BRANCHES_BY_ROOT =
          KvStoreColumn.create(
              ID_OFFSET + 6, BYTES32_SERIALIZER, COMPRESSED_BRANCH_INFO_KV_STORE_SERIALIZER);

  private final KvStoreColumn<UInt64, SignedBeaconBlock> finalizedBlocksBySlot;
  private final KvStoreColumn<Bytes32, SignedBeaconBlock> nonCanonicalBlocksByRoot;

  public V6TreeSchemaFinalized(final Spec spec) {
    finalizedBlocksBySlot =
        KvStoreColumn.create(
            ID_OFFSET + 7, UINT64_SERIALIZER, KvStoreSerializer.createSignedBlockSerializer(spec));
    nonCanonicalBlocksByRoot =
        KvStoreColumn.create(
            ID_OFFSET + 8, BYTES32_SERIALIZER, KvStoreSerializer.createSignedBlockSerializer(spec));
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
  public KvStoreColumn<UInt64, Bytes32> getColumnFinalizedStateRootsBySlot() {
    return FINALIZED_STATE_ROOTS_BY_SLOT;
  }

  @Override
  public KvStoreColumn<Bytes32, Bytes> getColumnFinalizedStateMerkleTreeLeaves() {
    return FINALIZED_STATE_TREE_LEAVES_BY_ROOT;
  }

  @Override
  public KvStoreColumn<Bytes32, CompressedBranchInfo> getColumnFinalizedStateMerkleTreeBranches() {
    return FINALIZED_STATE_TREE_BRANCHES_BY_ROOT;
  }
}
