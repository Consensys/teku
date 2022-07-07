/*
 * Copyright ConsenSys Software Inc., 2022
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

package tech.pegasys.teku.spec.datastructures.eth1;

import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.UINT64_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Eth1CacheSchema extends ContainerSchema2<Eth1Cache, DepositTreeSnapshot, SszUInt64> {

  public Eth1CacheSchema(final DepositTreeSnapshotSchema depositTreeSnapshotSchema) {
    super(
        "Eth1Cache",
        namedSchema("finalized_deposit_tree_snapshot", depositTreeSnapshotSchema),
        namedSchema("latest_finalized_deposits_block", UINT64_SCHEMA));
  }

  public Eth1Cache create(
      final DepositTreeSnapshot finalizedDepositTreeSnapshot,
      final UInt64 latestFinalizedDepositsBlock) {
    return new Eth1Cache(
        this, finalizedDepositTreeSnapshot, SszUInt64.of(latestFinalizedDepositsBlock));
  }

  @Override
  public Eth1Cache createFromBackingNode(TreeNode node) {
    return new Eth1Cache(this, node);
  }
}
