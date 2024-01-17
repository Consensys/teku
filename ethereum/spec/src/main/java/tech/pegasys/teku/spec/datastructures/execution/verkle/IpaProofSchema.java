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
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class IpaProofSchema
    extends ContainerSchema3<IpaProof, SszVector<SszBytes32>, SszVector<SszBytes32>, SszBytes32> {

  static final SszFieldName FIELD_CL = () -> "cl";

  public IpaProofSchema(final int ipaProofDepth) {
    super(
        "IpaProof",
        namedSchema(
            FIELD_CL, SszVectorSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, ipaProofDepth)),
        namedSchema(
            "cr", SszVectorSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, ipaProofDepth)),
        namedSchema("final_evaluation", SszPrimitiveSchemas.BYTES32_SCHEMA));
  }

  @SuppressWarnings("unchecked")
  public SszVectorSchema<SszBytes32, SszVector<SszBytes32>> getClSchema() {
    return (SszVectorSchema<SszBytes32, SszVector<SszBytes32>>)
        getChildSchema(getFieldIndex(FIELD_CL));
  }

  public IpaProof create(
      final List<Bytes32> cl, final List<Bytes32> cr, final Bytes32 finalEvaluation) {
    return new IpaProof(this, cl, cr, finalEvaluation);
  }

  @Override
  public IpaProof createFromBackingNode(TreeNode node) {
    return new IpaProof(this, node);
  }
}
