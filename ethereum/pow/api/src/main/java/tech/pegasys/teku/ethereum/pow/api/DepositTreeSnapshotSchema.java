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

package tech.pegasys.teku.ethereum.pow.api;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class DepositTreeSnapshotSchema
    extends ContainerSchema5<
        DepositTreeSnapshot, SszList<SszBytes32>, SszBytes32, SszUInt64, SszBytes32, SszUInt64> {

  public DepositTreeSnapshotSchema(final long depositTreeDepth) {
    super(
        "DepositTreeSnapshot",
        namedSchema(
            "finalized",
            SszListSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, depositTreeDepth)),
        namedSchema("deposit_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("deposit_count", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("execution_block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("execution_block_height", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  @Override
  public DepositTreeSnapshot createFromBackingNode(final TreeNode node) {
    return new DepositTreeSnapshot(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SszBytes32, ? extends SszList<SszBytes32>> getFinalizedSchema() {
    return (SszListSchema<SszBytes32, ? extends SszList<SszBytes32>>) getChildSchema(0);
  }
}
