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

import static tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema.MAX_LIST_MAX_LENGTH;
import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.BYTES32_SCHEMA;
import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.UINT64_SCHEMA;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32List;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.impl.SszBytes32ListSchemaImpl;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class DepositTreeSnapshotSchema
    extends ContainerSchema3<DepositTreeSnapshot, SszBytes32List, SszUInt64, SszBytes32> {
  // TODO: clarify list max size
  protected static final SszBytes32ListSchemaImpl<SszBytes32List> BYTES32_LIST_SCHEMA =
      new SszBytes32ListSchemaImpl<>(MAX_LIST_MAX_LENGTH);

  public DepositTreeSnapshotSchema() {
    super(
        "DepositTreeSnapshot",
        namedSchema("finalized", BYTES32_LIST_SCHEMA),
        namedSchema("deposits", UINT64_SCHEMA),
        namedSchema("execution_block_hash", BYTES32_SCHEMA));
  }

  public DepositTreeSnapshot create(
      final List<Bytes32> finalized, final UInt64 deposits, final Bytes32 executionBlockHash) {
    return new DepositTreeSnapshot(this, finalized, deposits, executionBlockHash);
  }

  @Override
  public DepositTreeSnapshot createFromBackingNode(TreeNode node) {
    return new DepositTreeSnapshot(this, node);
  }
}
