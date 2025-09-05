/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.spec.datastructures.execution;

import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class ExecutionProofSchema
    extends ContainerSchema4<ExecutionProof, SszBytes32, SszUInt64, SszUInt64, SszVector<SszByte>> {

  // Assuming max size of proof_data is 65536 bytes, in reality this should be much less
  // since it's just a concat of a couple of hashes plus some extra bytes.
  // need to confirm with Kev
  static final long MAX_PROOF_DATA_SIZE = 65536;

  public ExecutionProofSchema() {
    super(
        "ExecutionProof",
        namedSchema("block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("subnet_id", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("version", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(
            "proof_data",
            SszVectorSchema.create(SszPrimitiveSchemas.BYTE_SCHEMA, MAX_PROOF_DATA_SIZE)));
  }

  public ExecutionProof create(
      final SszBytes32 blockHash,
      final SszUInt64 subnetId,
      final SszUInt64 version,
      final SszVector<SszByte> proofData) {
    return new ExecutionProof(this, blockHash, subnetId, version, proofData);
  }

  @Override
  public ExecutionProof createFromBackingNode(final TreeNode node) {
    return new ExecutionProof(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszVectorSchema<SszByte, SszVector<SszByte>> getProofDataSchema() {
    return (SszVectorSchema<SszByte, SszVector<SszByte>>) getFieldSchema3();
  }
}
