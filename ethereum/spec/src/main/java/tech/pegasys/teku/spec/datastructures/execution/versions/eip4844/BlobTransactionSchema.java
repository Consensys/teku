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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip4844;

import java.util.List;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszUnion;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema11;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszUnionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class BlobTransactionSchema
    extends ContainerSchema11<
        BlobTransaction,
        SszUInt256,
        SszUInt64,
        SszUInt256,
        SszUInt256,
        SszUInt64,
        SszUnion,
        SszUInt256,
        SszByteList,
        SszList<AccessTuple>,
        SszUInt256,
        SszList<SszBytes32>> {

  private static final long MAX_VERSIONED_HASHES_LIST_SIZE = 16777216; // 2**24
  private static final long MAX_CALLDATA_SIZE = 16777216; // 2**24
  private static final long MAX_ACCESS_LIST_SIZE = 16777216; // 2**24

  BlobTransactionSchema() {
    super(
        "BlobTransaction",
        namedSchema("chain_id", SszPrimitiveSchemas.UINT256_SCHEMA),
        namedSchema("nonce", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("max_priority_fee_per_gas", SszPrimitiveSchemas.UINT256_SCHEMA),
        namedSchema("max_fee_per_gas", SszPrimitiveSchemas.UINT256_SCHEMA),
        namedSchema("gas", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(
            "to",
            SszUnionSchema.create(
                List.of(
                    SszPrimitiveSchemas.NONE_SCHEMA, SszByteVectorSchema.create(Bytes20.SIZE)))),
        namedSchema("value", SszPrimitiveSchemas.UINT256_SCHEMA),
        namedSchema("data", SszByteListSchema.create(MAX_CALLDATA_SIZE)),
        namedSchema(
            "access_list", SszListSchema.create(AccessTuple.SSZ_SCHEMA, MAX_ACCESS_LIST_SIZE)),
        namedSchema("max_fee_per_data_gas", SszPrimitiveSchemas.UINT256_SCHEMA),
        namedSchema(
            "blob_versioned_hashes",
            SszListSchema.create(
                SszPrimitiveSchemas.BYTES32_SCHEMA, MAX_VERSIONED_HASHES_LIST_SIZE)));
  }

  @Override
  public BlobTransaction createFromBackingNode(final TreeNode node) {
    return new BlobTransaction(this, node);
  }
}
