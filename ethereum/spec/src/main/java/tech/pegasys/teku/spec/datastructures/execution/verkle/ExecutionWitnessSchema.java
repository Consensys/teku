/*
 * Copyright Consensys Software Inc., 2023
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

package tech.pegasys.teku.spec.datastructures.execution.verkle;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class ExecutionWitnessSchema
    extends ContainerSchema3<ExecutionWitness, SszList<StemStateDiff>, VerkleProof, SszBytes32> {

  static final SszFieldName FIELD_STATE_DIFF = () -> "state_diff";
  static final SszFieldName FIELD_VERKLE_PROOF = () -> "verkle_proof";
  static final SszFieldName FIELD_PARENT_STATE_ROOT = () -> "parent_state_root";

  public ExecutionWitnessSchema(
      final int maxStems,
      final StemStateDiffSchema stemStateDiffSchema,
      final VerkleProofSchema verkleProofSchema) {
    super(
        "ExecutionWitness",
        namedSchema(FIELD_STATE_DIFF, SszListSchema.create(stemStateDiffSchema, maxStems)),
        namedSchema(FIELD_VERKLE_PROOF, verkleProofSchema),
        namedSchema(FIELD_PARENT_STATE_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA));
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<StemStateDiff, SszList<StemStateDiff>> getStateDiffSchema() {
    return (SszListSchema<StemStateDiff, SszList<StemStateDiff>>)
        getChildSchema(getFieldIndex(FIELD_STATE_DIFF));
  }

  public VerkleProofSchema getVerkleProofSchema() {
    return (VerkleProofSchema) getChildSchema(getFieldIndex(FIELD_VERKLE_PROOF));
  }

  public ExecutionWitness create(
      final List<StemStateDiff> stateDiffList,
      final VerkleProof verkleProof,
      final Bytes32 parentStateRoot) {
    return new ExecutionWitness(this, stateDiffList, verkleProof, parentStateRoot);
  }

  @Override
  public ExecutionWitness createFromBackingNode(TreeNode node) {
    return new ExecutionWitness(this, node);
  }
}
