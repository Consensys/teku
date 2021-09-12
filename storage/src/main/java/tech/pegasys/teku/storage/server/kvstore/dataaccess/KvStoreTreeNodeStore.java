/*
 * Copyright 2021 ConsenSys AG.
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

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.tree.LeafDataNode;
import tech.pegasys.teku.ssz.tree.TreeNode;
import tech.pegasys.teku.ssz.tree.TreeNodeSource.CompressedBranchInfo;
import tech.pegasys.teku.ssz.tree.TreeNodeStore;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor;
import tech.pegasys.teku.storage.server.kvstore.KvStoreAccessor.KvStoreTransaction;
import tech.pegasys.teku.storage.server.kvstore.schema.SchemaFinalizedTreeState;

public class KvStoreTreeNodeStore implements TreeNodeStore {

  private final KvStoreAccessor db;
  private final KvStoreTransaction transaction;
  private final SchemaFinalizedTreeState schema;

  public KvStoreTreeNodeStore(
      final KvStoreAccessor db,
      final KvStoreTransaction transaction,
      final SchemaFinalizedTreeState schema) {
    this.db = db;
    this.transaction = transaction;
    this.schema = schema;
  }

  @Override
  public boolean canSkipBranch(final Bytes32 root, final long gIndex) {
    return db.get(schema.getColumnFinalizedStateMerkleTreeBranches(), root).isPresent();
  }

  @Override
  public void storeBranchNode(
      final Bytes32 root, final long gIndex, final int depth, final Bytes32[] children) {
    transaction.put(
        schema.getColumnFinalizedStateMerkleTreeBranches(),
        root,
        new CompressedBranchInfo(depth, children));
  }

  @Override
  public void storeLeafNode(final TreeNode treeNode, final long gIndex) {
    checkArgument(treeNode instanceof LeafDataNode, "Can't store a non-leaf node");
    final LeafDataNode node = (LeafDataNode) treeNode;
    if (node.getData().size() > Bytes32.SIZE && !node.hashTreeRoot().isZero()) {
      transaction.put(
          schema.getColumnFinalizedStateMerkleTreeLeaves(), node.hashTreeRoot(), node.getData());
    }
  }
}
